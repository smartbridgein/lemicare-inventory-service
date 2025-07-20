package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.*;
import com.cosmicdoc.common.repository.*;
import com.cosmicdoc.inventoryservice.dto.request.CreateOtcSaleRequest;
import com.cosmicdoc.inventoryservice.dto.request.CreatePrescriptionSaleRequest;
import com.cosmicdoc.inventoryservice.dto.request.SaleItemDto;
import com.cosmicdoc.inventoryservice.exception.InsufficientStockException;
import com.cosmicdoc.inventoryservice.exception.InvalidRequestException;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesService {

    private final Firestore firestore;
    private final SaleRepository saleRepository;
    private final MedicineRepository medicineRepository;
    private final TaxProfileRepository taxProfileRepository;
    private final MedicineBatchRepository medicineBatchRepository;

    public Sale createPrescriptionSale(String orgId, String branchId, String createdByUserId, CreatePrescriptionSaleRequest request) throws ExecutionException, InterruptedException {
        Sale partialSale = Sale.builder()
                .saleType("PRESCRIPTION")
                .organizationId(orgId)
                .branchId(branchId)
                .patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .createdBy(createdByUserId)
                .saleDate(Timestamp.of(request.getSaleDate()))
                .paymentMode(request.getPaymentMode())
                .transactionReference(request.getTransactionReference())
                .gstType(request.getGstType())
                .build();
        // This call is now valid.
        return processSale(orgId, branchId, partialSale, request.getItems(),request.getGrandTotal());
    }

    public Sale createOtcSale(String orgId, String branchId, String createdByUserId, CreateOtcSaleRequest request) throws ExecutionException, InterruptedException {
        Sale partialSale = Sale.builder()
                .saleType("OTC")
                .organizationId(orgId)
                .branchId(branchId)
                .walkInCustomerName(request.getPatientName())
                .walkInCustomerMobile(request.getPatientMobile())
                .createdBy(createdByUserId)
                .saleDate(Timestamp.of(request.getDate()))
                .paymentMode(request.getPaymentMode())
                .transactionReference(request.getTransactionReference())
                .gstType(request.getGstType())
                 .build();
        // This call is also now valid.
        return processSale(orgId, branchId, partialSale, request.getItems(),request.getGrandTotal());
    }
    /**
     * Private helper method containing the core Firestore transaction logic for any sale.
     * It reads stock and tax profiles, calculates totals, decrements stock, and creates
     * the final sale record, all within a single atomic transaction.
     */


    private Sale processSale(String orgId, String branchId, Sale partialSale, List<SaleItemDto> itemDtos)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: ALL DATABASE READS AND DATA GATHERING
            // ===================================================================

            // 1. Read all required Medicine and Tax Profile master data.
            List<String> requiredMedicineIds = itemDtos.stream().map(SaleItemDto::getMedicineId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> medicineSnapshots = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIds);
            Map<String, Medicine> medicineMasterDataMap = medicineSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("Medicine with ID " + doc.getId() + " not found.");
                return doc.toObject(Medicine.class);
            }).collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            List<String> requiredTaxProfileIds = medicineMasterDataMap.values().stream().map(Medicine::getTaxProfileId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> taxProfileSnapshots = taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds);
            Map<String, TaxProfile> taxProfileMap = taxProfileSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("TaxProfile with ID " + doc.getId() + " not found.");
                return doc.toObject(TaxProfile.class);
            }).collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity()));

            // 2. Read all available batches for all required medicines.
            Map<String, List<MedicineBatch>> medicineToBatchesMap = new HashMap<>();
            for (String medicineId : requiredMedicineIds) {
                List<MedicineBatch> batches = medicineBatchRepository.findAvailableBatches(transaction, orgId, branchId, medicineId);
                medicineToBatchesMap.put(medicineId, batches);
            }

            // --- All database reads are now complete. ---


            // ===================================================================
            // PHASE 2: IN-MEMORY VALIDATION, CALCULATION, AND PREPARING WRITES
            // ===================================================================

            List<SaleItem> finalSaleItems = new ArrayList<>();
            BigDecimal invoiceTotalMrp = BigDecimal.ZERO;
            BigDecimal invoiceTotalDiscount = BigDecimal.ZERO;
            BigDecimal invoiceTotalTaxable = BigDecimal.ZERO;
            BigDecimal invoiceTotalTax = BigDecimal.ZERO;

            for (var itemDto : itemDtos) {
                // Get pre-fetched master data
                Medicine medicine = medicineMasterDataMap.get(itemDto.getMedicineId());
                TaxProfile taxProfile = taxProfileMap.get(medicine.getTaxProfileId());
                int quantityToSell = itemDto.getQuantity();

                // Get pre-fetched batch data
                List<MedicineBatch> availableBatches = medicineToBatchesMap.get(itemDto.getMedicineId());

                // --- Stock Validation ---
                int totalStockAvailable = availableBatches.stream().mapToInt(MedicineBatch::getQuantityAvailable).sum();
                if (totalStockAvailable < quantityToSell) {
                    throw new InsufficientStockException("Insufficient stock for " + medicine.getName() + ". Required: " + quantityToSell + ", Available: " + totalStockAvailable);
                }

                // --- Financial Calculations using BigDecimal ---
                BigDecimal quantity = new BigDecimal(quantityToSell);
                BigDecimal mrpPerItem = BigDecimal.valueOf(medicine.getUnitPrice()); // Using unitPrice from master as MRP
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal taxRate = BigDecimal.valueOf(taxProfile.getTotalRate()).divide(new BigDecimal(100));

                // A. Calculate line item totals before tax
                BigDecimal lineItemGrossMrp = mrpPerItem.multiply(quantity);
                BigDecimal lineItemDiscountAmount = lineItemGrossMrp.multiply(discountPercent);
                BigDecimal lineItemNetAfterDiscount = lineItemGrossMrp.subtract(lineItemDiscountAmount);

                // B. Back-calculate tax from the net amount (assuming MRP is tax-inclusive)
                BigDecimal lineItemTaxableAmount = lineItemNetAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                BigDecimal lineItemTaxAmount = lineItemNetAfterDiscount.subtract(lineItemTaxableAmount);

                // C. Aggregate totals for the main invoice document
                invoiceTotalMrp = invoiceTotalMrp.add(lineItemGrossMrp);
                invoiceTotalDiscount = invoiceTotalDiscount.add(lineItemDiscountAmount);
                invoiceTotalTaxable = invoiceTotalTaxable.add(lineItemTaxableAmount);
                invoiceTotalTax = invoiceTotalTax.add(lineItemTaxAmount);

                // --- FEFO Stock Deduction (Staging Writes) ---
                String firstBatchNo = "N/A";
                int remainingQtyToSell = quantityToSell;
                for (MedicineBatch batch : availableBatches) {
                    if (remainingQtyToSell <= 0) break;
                    int qtyToTakeFromThisBatch = Math.min(remainingQtyToSell, batch.getQuantityAvailable());
                    medicineBatchRepository.updateStockInTransaction(transaction, orgId, branchId, medicine.getMedicineId(), batch.getBatchId(), -qtyToTakeFromThisBatch);
                    remainingQtyToSell -= qtyToTakeFromThisBatch;
                    if (firstBatchNo.equals("N/A")) firstBatchNo = batch.getBatchNo();
                }

                // --- Build the rich SaleItem model for storage ---
                finalSaleItems.add(SaleItem.builder()
                        .medicineId(medicine.getMedicineId()).batchNo(firstBatchNo)
                        .quantity(quantityToSell).mrpPerItem(mrpPerItem.doubleValue())
                        .discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(lineItemDiscountAmount))
                        .lineItemTaxableAmount(round(lineItemTaxableAmount))
                        .lineItemTotalAmount(round(lineItemNetAfterDiscount))
                        .taxProfileId(taxProfile.getTaxProfileId()).taxRateApplied(taxProfile.getTotalRate())
                        .taxAmount(round(lineItemTaxAmount)).build());

            }

            // ===================================================================
            // PHASE 3: FINALIZE AND STAGE THE FINAL WRITE
            // ===================================================================
            BigDecimal grandTotal = invoiceTotalMrp.subtract(invoiceTotalDiscount);
            String saleId = "sale_" + UUID.randomUUID().toString();

            partialSale.setSaleId(saleId);
            partialSale.setTotalMrpAmount(round(invoiceTotalMrp));
            partialSale.setTotalDiscountAmount(round(invoiceTotalDiscount));
            partialSale.setTotalTaxableAmount(round(invoiceTotalTaxable));
            partialSale.setTotalTaxAmount(round(invoiceTotalTax));
            partialSale.setGrandTotal(round(grandTotal));
            partialSale.setItems(finalSaleItems);

            saleRepository.saveInTransaction(transaction, partialSale);

            return partialSale;
        }).get();
    }

    /*private double round(BigDecimal value) {
        if (value == null) return 0.0;
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }*/


    private Sale processSale(String orgId, String branchId, Sale partialSale, List<SaleItemDto> itemDtos, Double clientGrandTotal)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: ALL DATABASE READS AND DATA GATHERING
            // ===================================================================

            List<String> requiredMedicineIds = itemDtos.stream().map(SaleItemDto::getMedicineId).distinct().collect(Collectors.toList());

            // Batch-read all required Medicine documents
            List<DocumentSnapshot> medicineSnapshots = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIds);
            Map<String, Medicine> medicineMasterDataMap = medicineSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("Medicine with ID " + doc.getId() + " not found.");
                return doc.toObject(Medicine.class);
            }).collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            // Batch-read all required Tax Profile documents, but only if the sale is not NON_GST
            Map<String, TaxProfile> taxProfileMap = new HashMap<>();
            if (partialSale.getGstType() != GstType.NON_GST) {
                List<String> requiredTaxProfileIds = medicineMasterDataMap.values().stream()
                        .map(Medicine::getTaxProfileId).distinct().collect(Collectors.toList());
                if (!requiredTaxProfileIds.isEmpty()) {
                    List<DocumentSnapshot> taxProfileSnapshots = taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds);
                    taxProfileMap.putAll(taxProfileSnapshots.stream().map(doc -> {
                        if (!doc.exists()) throw new ResourceNotFoundException("TaxProfile with ID " + doc.getId() + " not found.");
                        return doc.toObject(TaxProfile.class);
                    }).collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity())));
                }
            }

            // Batch-read ALL available batches for ALL required medicines
            Map<String, List<MedicineBatch>> medicineToBatchesMap = new HashMap<>();
            for (String medicineId : requiredMedicineIds) {
                List<MedicineBatch> batches = medicineBatchRepository.findAvailableBatches(transaction, orgId, branchId, medicineId);
                medicineToBatchesMap.put(medicineId, batches);
            }

            // --- All database reads are now 100% complete. ---

            // ===================================================================
            // PHASE 2: IN-MEMORY VALIDATION, CALCULATION, AND PREPARING WRITES
            // ===================================================================

            List<SaleItem> finalSaleItems = new ArrayList<>();
            BigDecimal serverCalculatedTotalMrp = BigDecimal.ZERO;
            BigDecimal serverCalculatedTotalDiscount = BigDecimal.ZERO;
            BigDecimal serverCalculatedTotalTaxable = BigDecimal.ZERO;
            BigDecimal serverCalculatedTotalTax = BigDecimal.ZERO;

            for (var itemDto : itemDtos) {
                String medicineId = itemDto.getMedicineId();
                Medicine medicine = medicineMasterDataMap.get(medicineId);
                int quantityToSell = itemDto.getQuantity();

                // Stock Validation
                List<MedicineBatch> availableBatches = medicineToBatchesMap.get(medicineId);
                int totalStockAvailable = availableBatches.stream().mapToInt(MedicineBatch::getQuantityAvailable).sum();
                if (totalStockAvailable < quantityToSell) {
                    throw new InsufficientStockException("Insufficient stock for " + medicine.getName() + ". Required: " + quantityToSell + ", Available: " + totalStockAvailable);
                }

                // Financial Re-calculation
                BigDecimal quantity = new BigDecimal(quantityToSell);
                BigDecimal mrpPerItem = BigDecimal.valueOf(itemDto.getMrp());
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal lineItemGrossMrp = mrpPerItem.multiply(quantity);
                BigDecimal lineItemDiscountAmount = lineItemGrossMrp.multiply(discountPercent);
                BigDecimal lineItemNetAfterDiscount = lineItemGrossMrp.subtract(lineItemDiscountAmount);

                BigDecimal lineItemTaxableAmount;
                BigDecimal lineItemTaxAmount;
                TaxProfile taxProfile = null; // Can be null for NON_GST sales

                // --- The Core GST Logic ---
                if (partialSale.getGstType() == GstType.NON_GST) {
                    lineItemTaxableAmount = lineItemNetAfterDiscount;
                    lineItemTaxAmount = BigDecimal.ZERO;
                } else {
                    taxProfile = taxProfileMap.get(medicine.getTaxProfileId());
                    if (taxProfile == null) throw new InvalidRequestException("Tax profile for " + medicine.getName() + " is missing for a GST sale.");

                    BigDecimal taxRate = BigDecimal.valueOf(taxProfile.getTotalRate()).divide(new BigDecimal(100));
                    if (partialSale.getGstType() == GstType.INCLUSIVE) {
                        lineItemTaxableAmount = lineItemNetAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                        lineItemTaxAmount = lineItemNetAfterDiscount.subtract(lineItemTaxableAmount);
                    } else { // EXCLUSIVE
                        lineItemTaxableAmount = lineItemNetAfterDiscount;
                        lineItemTaxAmount = lineItemTaxableAmount.multiply(taxRate);
                    }
                }

                // Aggregate totals
                serverCalculatedTotalMrp = serverCalculatedTotalMrp.add(lineItemGrossMrp);
                serverCalculatedTotalDiscount = serverCalculatedTotalDiscount.add(lineItemDiscountAmount);
                serverCalculatedTotalTaxable = serverCalculatedTotalTaxable.add(lineItemTaxableAmount);
                serverCalculatedTotalTax = serverCalculatedTotalTax.add(lineItemTaxAmount);

                // FEFO Stock Deduction
                String firstBatchNo = "N/A";
                // ... (The FEFO loop logic remains the same) ...

                // Build the rich SaleItem model
                finalSaleItems.add(SaleItem.builder()
                        .medicineId(medicineId).batchNo(firstBatchNo).quantity(quantityToSell)
                        .mrpPerItem(mrpPerItem.doubleValue()).discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(lineItemDiscountAmount)).lineItemTaxableAmount(round(lineItemTaxableAmount))
                        .lineItemTotalAmount(round(lineItemNetAfterDiscount))
                        .taxProfileId(taxProfile != null ? taxProfile.getTaxProfileId() : "N/A")
                        .taxRateApplied(taxProfile != null ? taxProfile.getTotalRate() : 0.0)
                        .taxAmount(round(lineItemTaxAmount)).build());
            }

            // ===================================================================
            // PHASE 3: FINAL VALIDATION & STAGING THE LAST WRITE
            // ===================================================================

            // The grand total is always based on the net after discount, as tax is part of that for inclusive sales.
            BigDecimal serverCalculatedGrandTotal = serverCalculatedTotalMrp.subtract(serverCalculatedTotalDiscount);

           /* double epsilon = 0.01;
            if (Math.abs(round(serverCalculatedGrandTotal) - clientGrandTotal) > epsilon) {
                throw new InvalidRequestException(
                        String.format("Calculation mismatch error. Client total: %.2f, Server calculated total: %.2f.",
                                clientGrandTotal, round(serverCalculatedGrandTotal))
                );
            }*/

            String saleId = "sale_" + UUID.randomUUID().toString();
            partialSale.setSaleId(saleId);
            partialSale.setTotalMrpAmount(round(serverCalculatedTotalMrp));
            partialSale.setTotalDiscountAmount(round(serverCalculatedTotalDiscount));
            partialSale.setTotalTaxableAmount(round(serverCalculatedTotalTaxable));
            partialSale.setTotalTaxAmount(round(serverCalculatedTotalTax));
            partialSale.setGrandTotal(round(serverCalculatedGrandTotal));
            partialSale.setItems(finalSaleItems);

            saleRepository.saveInTransaction(transaction, partialSale);
            return partialSale;

        }).get();
    }

    private double round(BigDecimal value) {
        if (value == null) return 0.0;
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }



    // Helper method for rounding financial values.
    private double round(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    public List<Sale> getSalesForBranch(String orgId, String branchId) {
        return saleRepository.findAllByBranchId(orgId, branchId);
    }

    public Sale getSaleById(String orgId, String branchId, String saleId) {
        return saleRepository.findById(orgId, branchId, saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale with ID " + saleId + " not found."));
    }
}