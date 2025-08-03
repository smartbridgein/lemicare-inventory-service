package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.*;
import com.cosmicdoc.common.repository.*;
import com.cosmicdoc.common.util.IdGenerator;
import com.cosmicdoc.inventoryservice.dto.request.*;
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
                .doctorName(request.getDoctorName())
                .doctorName(request.getDoctorName())
                .address(request.getAddress())
                .age(request.getAge())
                .gender(request.getGender())
                .createdBy(createdByUserId)
                .saleDate(Timestamp.of(request.getSaleDate()))
                .paymentMode(request.getPaymentMode())
                .transactionReference(request.getTransactionReference())
                .gstType(request.getGstType())
                .overallAdjustmentType(request.getOverallAdjustmentType())
                .overallAdjustmentValue(request.getOverallAdjustmentValue() != null ? request.getOverallAdjustmentValue() : 0.0)
                .build();

        // This call is now valid.
        return processSaleCreation(orgId, branchId, partialSale, request.getItems(),request.getGrandTotal());
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
                .doctorName(request.getDoctorName())
                .address(request.getAddress())
                .age(request.getAge())
                .gender(request.getGender())
                .overallAdjustmentType(request.getOverallAdjustmentType())
                .overallAdjustmentValue(request.getOverallAdjustmentValue() != null ? request.getOverallAdjustmentValue() : 0.0)
                 .build();
        // This call is also now valid.
        return processSaleCreation(orgId, branchId, partialSale, request.getItems(),request.getGrandTotal());
    }
    /**
     * Private helper method containing the core Firestore transaction logic for any sale.
     * It reads stock and tax profiles, calculates totals, decrements stock, and creates
     * the final sale record, all within a single atomic transaction.
     */


   /* private Sale processSale(String orgId, String branchId, Sale partialSale, List<SaleItemDto> itemDtos)
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
    }*/

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

                int remainingQtyToSell = quantityToSell;
// This list will store the details of which batches we use for THIS line item.
                List<BatchAllocation> allocations = new ArrayList<>();

                for (MedicineBatch batch : availableBatches) {
                    if (remainingQtyToSell <= 0) {
                        break; // Stop if we have fulfilled the required quantity
                    }

                    // Determine how much to take from the current batch
                    int qtyToTakeFromThisBatch = Math.min(remainingQtyToSell, batch.getQuantityAvailable());

                    // STAGE WRITE: Atomically decrement the stock from this specific batch document.
                    medicineBatchRepository.updateStockInTransaction(
                            transaction,
                            orgId,
                            branchId,
                            medicine.getMedicineId(),
                            batch.getBatchId(),
                            -qtyToTakeFromThisBatch
                    );

                    // Record the allocation: which batch we used and how much we took from it.
                    allocations.add(BatchAllocation.builder()
                            .batchId(batch.getBatchId())
                            .batchNo(batch.getBatchNo())
                            .quantityTaken(qtyToTakeFromThisBatch)
                            .expiryDate(batch.getExpiryDate())
                            .build());

                    // Decrement the amount we still need to find.
                    remainingQtyToSell -= qtyToTakeFromThisBatch;
                }


                // Build the rich SaleItem model
                finalSaleItems.add(SaleItem.builder()
                        .medicineId(medicineId)

                        .quantity(quantityToSell) // The total quantity for this line item
                        .batchAllocations(allocations) // <-- The new, detailed list of batches used
                        .mrpPerItem(mrpPerItem.doubleValue())
                        .discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(lineItemDiscountAmount))
                        .lineItemTaxableAmount(round(lineItemTaxableAmount))
                        .lineItemTotalAmount(round(lineItemNetAfterDiscount))
                        .taxProfileId(taxProfile != null ? taxProfile.getTaxProfileId() : "N/A")
                        .taxRateApplied(taxProfile != null ? taxProfile.getTotalRate() : 0.0)
                        .taxAmount(round(lineItemTaxAmount))
                        .build());
            }

            // ===================================================================
            // PHASE 3: FINAL VALIDATION & STAGING THE LAST WRITE
            // ===================================================================

            // Calculate grand total based on GST type
            BigDecimal serverCalculatedGrandTotal = serverCalculatedTotalMrp.subtract(serverCalculatedTotalDiscount);
            
            // For EXCLUSIVE GST, add the calculated tax amount to the grand total
            // For INCLUSIVE GST, tax is already included in the MRP, so no addition needed
            // For NON_GST, no tax to add
            if (partialSale.getGstType() == GstType.EXCLUSIVE) {
                serverCalculatedGrandTotal = serverCalculatedGrandTotal.add(serverCalculatedTotalTax);
            }

           /* double epsilon = 0.01;
            if (Math.abs(round(serverCalculatedGrandTotal) - clientGrandTotal) > epsilon) {
                throw new InvalidRequestException(
                        String.format("Calculation mismatch error. Client total: %.2f, Server calculated total: %.2f.",
                                clientGrandTotal, round(serverCalculatedGrandTotal))
                );
            }*/

            String saleId = IdGenerator.newId("SALE");
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

    /**
     * Performs a "hard delete" on a Sale, permanently removing the sale record
     * and atomically reversing the stock deductions from the original batches.
     *
     * WARNING: This is a destructive operation and should be used with caution.
     * It's often better to implement a "cancel" status (soft delete).
     *
     * @param orgId The organization ID from the security context.
     * @param branchId The branch ID from the security context.
     * @param saleId The ID of the sale to delete.
     */
    public void deleteSale(String orgId, String branchId, String saleId)
            throws ExecutionException, InterruptedException {

        firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: READ the Sale to be Deleted
            // ===================================================================
            Sale saleToDelete = saleRepository.findById(transaction, orgId, branchId, saleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale with ID " + saleId + " not found."));

            // ===================================================================
            // PHASE 2: STAGE ALL WRITES (Stock Reversals & Deletion)
            // ===================================================================

            // --- THIS IS THE CORRECTED LOGIC ---

            // 1. Loop through each line item in the sale.
            for (SaleItem item : saleToDelete.getItems()) {

                // 2. For each item, loop through its batch allocations.
                //    This correctly handles cases where one sale item used multiple batches.
                for (BatchAllocation allocation : item.getBatchAllocations()) {

                    // 3. STAGE WRITE: Atomically add the stock back to the specific batch.
                    //    The quantity to add back is the 'quantityTaken' from the allocation record.
                    //    We use the 'batchId' for a direct and efficient update.
                    medicineBatchRepository.updateStockInTransaction(
                            transaction,
                            orgId,
                            branchId,
                            item.getMedicineId(),
                            allocation.getBatchId(), // Use the specific batch document ID
                            allocation.getQuantityTaken() // Add back the exact quantity that was taken
                    );
                }
            }
            // --- END OF CORRECTED LOGIC ---

            // 4. STAGE WRITE: Permanently delete the Sale document itself.
            saleRepository.deleteByIdInTransaction(transaction, orgId, branchId, saleId);

            return null; // Return null for a void operation
        }).get();
    }

    public Sale updatePrescriptionSale(String orgId, String branchId, String updatedByUserId, String saleId, UpdatePrescriptionSaleRequest request) throws ExecutionException, InterruptedException {
        Sale updatedHeader = Sale.builder()
                .saleType("PRESCRIPTION").patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .prescriptionDate(Timestamp.of(request.getPrescriptionDate()))
                .saleDate(Timestamp.of(request.getSaleDate()))
                .paymentMode(request.getPaymentMode())
                .doctorName(request.getDoctorName())
                .address(request.getAddress())
                .gender(request.getGender())
                .age(request.getAge())
                .gstType(request.getGstType())
                .transactionReference(request.getTransactionReference())
                .gstType(request.getGstType())
                . overallAdjustmentType(request.getOverallAdjustmentType())
                .overallAdjustmentValue(request.getOverallAdjustmentValue() != null ? request.getOverallAdjustmentValue() : 0.0).build();

        return processSaleUpdate(orgId, branchId, updatedByUserId, saleId, updatedHeader, request.getItems(), request.getGrandTotal());
    }

    public Sale updateOtcSale(String orgId, String branchId, String updatedByUserId, String saleId, UpdateOtcSaleRequest request) throws ExecutionException, InterruptedException {
        Sale updatedHeader = Sale.builder()
                .saleType("OTC").walkInCustomerName(request.getPatientName())
                .walkInCustomerMobile(request.getPatientMobile())
                .doctorName(request.getDoctorName())
                .address(request.getAddress())
                .gender(request.getGender())
                .age(request.getAge())
                .saleDate(Timestamp.of(request.getDate()))
                .paymentMode(request.getPaymentMode())
                .transactionReference(request.getTransactionReference())
                . overallAdjustmentType(request.getOverallAdjustmentType())
                .overallAdjustmentValue(request.getOverallAdjustmentValue() != null ? request.getOverallAdjustmentValue() : 0.0)
                .gstType(request.getGstType()).build();
        return processSaleUpdate(orgId, branchId, updatedByUserId, saleId, updatedHeader, request.getItems(), request.getGrandTotal());
    }

    private Sale processSaleUpdate(String orgId, String branchId, String updatedByUserId, String saleId, Sale updatedHeader, List<SaleItemDto> itemDtos, Double clientGrandTotal)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: READ ALL ORIGINAL & NEW DATA
            // ===================================================================

            // 1. READ the original Sale document to be updated.
            Sale originalSale = saleRepository.findById(transaction, orgId, branchId, saleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale with ID " + saleId + " not found to update."));

            // 2. READ master data (Medicines, TaxProfiles) for the NEW request items.
            List<String> requiredMedicineIdsForNewSale = itemDtos.stream().map(SaleItemDto::getMedicineId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> medicineSnapshots = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIdsForNewSale);
            Map<String, Medicine> medicineMasterDataMap = medicineSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("Medicine with ID " + doc.getId() + " not found.");
                return doc.toObject(Medicine.class);
            }).collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            Map<String, TaxProfile> taxProfileMap = new HashMap<>();
            if (updatedHeader.getGstType() != GstType.NON_GST) {
                List<String> requiredTaxProfileIds = medicineMasterDataMap.values().stream()
                        .map(Medicine::getTaxProfileId).distinct().collect(Collectors.toList());
                if (!requiredTaxProfileIds.isEmpty()) {
                    taxProfileMap.putAll(taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds)
                            .stream().map(doc -> doc.toObject(TaxProfile.class))
                            .collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity())));
                }
            }


            // 3. READ ALL necessary batch data for both reversal and new deduction.

            Set<String> allInvolvedMedicineIds = new HashSet<>(requiredMedicineIdsForNewSale);
            originalSale.getItems().forEach(item -> allInvolvedMedicineIds.add(item.getMedicineId()));

            Map<String, List<MedicineBatch>> medicineToBatchesMap = allInvolvedMedicineIds.stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            medicineId -> {
                                try {
                                    return medicineBatchRepository.findAvailableBatches(transaction, orgId, branchId, medicineId);
                                } catch (ExecutionException | InterruptedException e) {
                                    throw new RuntimeException(e); // Transactions handle runtime exceptions
                                }
                            }
                    ));
            // --- All database reads are now 100% complete. ---

            // ===================================================================
            // PHASE 2: REVERSE OLD STOCK (Staging Writes)
            // ===================================================================
            for (SaleItem oldItem : originalSale.getItems()) {
                if (oldItem.getBatchAllocations() == null) continue;
                for (BatchAllocation allocation : oldItem.getBatchAllocations()) {
                    // Stage the reversal: INCREMENT the stock back to the specific batch.
                    medicineBatchRepository.updateStockInTransaction(transaction, orgId, branchId, oldItem.getMedicineId(), allocation.getBatchId(), allocation.getQuantityTaken());

                    medicineRepository.updateStockInTransaction(transaction, orgId, branchId, oldItem.getMedicineId(), allocation.getQuantityTaken());
                }
            }


            // ===================================================================
            // PHASE 3: RE-APPLY NEW LOGIC (CALCULATIONS & NEW WRITES)
            // ===================================================================

            List<SaleItem> newSaleItems = new ArrayList<>();
            BigDecimal serverCalculatedTotalMrp = BigDecimal.ZERO;
            BigDecimal serverCalculatedTotalDiscount = BigDecimal.ZERO;
            BigDecimal serverCalculatedTotalTaxable = BigDecimal.ZERO;
            BigDecimal serverCalculatedTotalTax = BigDecimal.ZERO;

            for (var itemDto : itemDtos) {
                String medicineId = itemDto.getMedicineId();
                Medicine medicine = medicineMasterDataMap.get(medicineId);
                int quantityToSell = itemDto.getQuantity();

                List<MedicineBatch> availableBatches = medicineToBatchesMap.get(medicineId);
                int totalStockAvailable = availableBatches.stream().mapToInt(MedicineBatch::getQuantityAvailable).sum();
                if (totalStockAvailable < quantityToSell) {
                    throw new InsufficientStockException("Insufficient stock for " + medicine.getName() + ". Required: " + quantityToSell + ", Available: " + totalStockAvailable);
                }

                // Financial Calculation
                BigDecimal quantity = new BigDecimal(quantityToSell);
                BigDecimal mrpPerItem = BigDecimal.valueOf(itemDto.getMrp());
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal lineItemGrossMrp = mrpPerItem.multiply(quantity);
                BigDecimal lineItemDiscountAmount = lineItemGrossMrp.multiply(discountPercent);
                BigDecimal lineItemNetAfterDiscount = lineItemGrossMrp.subtract(lineItemDiscountAmount);
                BigDecimal lineItemTaxableAmount;
                BigDecimal lineItemTaxAmount;
                TaxProfile taxProfile = null;

                if (updatedHeader.getGstType() == GstType.NON_GST) {
                    lineItemTaxableAmount = lineItemNetAfterDiscount;
                    lineItemTaxAmount = BigDecimal.ZERO;
                } else {
                    taxProfile = taxProfileMap.get(itemDto.getTaxProfileId());
                    if (taxProfile == null) {
                        throw new InvalidRequestException("Tax profile with ID '" + itemDto.getTaxProfileId() + "' not found or is invalid.");
                    }

                    BigDecimal taxRate = BigDecimal.valueOf(taxProfile.getTotalRate()).divide(new BigDecimal(100));
                    if (updatedHeader.getGstType() == GstType.INCLUSIVE) {
                        lineItemTaxableAmount = lineItemNetAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                        lineItemTaxAmount = lineItemNetAfterDiscount.subtract(lineItemTaxableAmount);
                    } else { // EXCLUSIVE
                        lineItemTaxableAmount = lineItemNetAfterDiscount;
                        lineItemTaxAmount = lineItemTaxableAmount.multiply(taxRate);
                    }
                }

                serverCalculatedTotalMrp = serverCalculatedTotalMrp.add(lineItemGrossMrp);
                serverCalculatedTotalDiscount = serverCalculatedTotalDiscount.add(lineItemDiscountAmount);
                serverCalculatedTotalTaxable = serverCalculatedTotalTaxable.add(lineItemTaxableAmount);
                serverCalculatedTotalTax = serverCalculatedTotalTax.add(lineItemTaxAmount);

                // FEFO Stock Deduction and Batch Allocation for the NEW items
                List<BatchAllocation> newAllocations = new ArrayList<>();
                int remainingQtyToSell = quantityToSell;
                for (MedicineBatch batch : availableBatches) {
                    if (remainingQtyToSell <= 0) break;
                    int qtyToTakeFromThisBatch = Math.min(remainingQtyToSell, batch.getQuantityAvailable());
                    medicineBatchRepository.updateStockInTransaction(transaction, orgId, branchId, medicineId, batch.getBatchId(), -qtyToTakeFromThisBatch);
                    medicineRepository.updateStockInTransaction(transaction, orgId, branchId, medicineId, -qtyToTakeFromThisBatch);

                    newAllocations.add(BatchAllocation.builder().batchId(batch.getBatchId()).batchNo(batch.getBatchNo()).quantityTaken(qtyToTakeFromThisBatch).expiryDate(batch.getExpiryDate()).build());
                    remainingQtyToSell -= qtyToTakeFromThisBatch;
                }

                BigDecimal lineItemTotalAmount = lineItemTaxableAmount.add(lineItemTaxAmount);
                newSaleItems.add(SaleItem.builder()
                        .medicineId(medicineId)
                        .quantity(quantityToSell)
                        .batchAllocations(newAllocations)
                        .mrpPerItem(mrpPerItem.doubleValue())
                        .discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(lineItemDiscountAmount))
                        .lineItemTaxableAmount(round(lineItemTaxableAmount))
                        .lineItemTotalAmount(round(lineItemTotalAmount)) // <-- Use the correct final total
                        .taxProfileId(taxProfile != null ? taxProfile.getTaxProfileId() : "N/A")
                        .taxRateApplied(taxProfile != null ? taxProfile.getTotalRate() : 0.0)
                        .taxAmount(round(lineItemTaxAmount))
                        .build());
            }

            BigDecimal subTotal;
            if (updatedHeader.getGstType() == GstType.INCLUSIVE) {
                subTotal = serverCalculatedTotalMrp.subtract(serverCalculatedTotalDiscount);
            } else { // EXCLUSIVE or NON_GST
                subTotal = serverCalculatedTotalTaxable.add(serverCalculatedTotalTax);
            }

            //    Calculate the overall adjustment amount based on the NEW data.
            BigDecimal calculatedOverallAdjustmentAmount = BigDecimal.ZERO;
            if (updatedHeader.getOverallAdjustmentType() != null && updatedHeader.getOverallAdjustmentValue() > 0) {
                BigDecimal adjustmentValue = BigDecimal.valueOf(updatedHeader.getOverallAdjustmentValue());
                switch (updatedHeader.getOverallAdjustmentType()) {
                    case PERCENTAGE_DISCOUNT:
                        calculatedOverallAdjustmentAmount = serverCalculatedTotalTaxable.multiply(adjustmentValue.divide(new BigDecimal(100)));
                        break;
                    case FIXED_DISCOUNT:
                        calculatedOverallAdjustmentAmount = adjustmentValue;
                        break;
                    case ADDITIONAL_CHARGE:
                        calculatedOverallAdjustmentAmount = adjustmentValue.negate();
                        break;
                }
            }

            // C. Calculate the final grand total using the new adjustment.
            BigDecimal serverCalculatedGrandTotal = subTotal.subtract(calculatedOverallAdjustmentAmount);

            // D. FINAL VALIDATION STEP
           /* double epsilon = 0.01;
            if (Math.abs(round(serverCalculatedGrandTotal) - clientGrandTotal) > epsilon) {
                throw new InvalidRequestException(String.format("Calculation mismatch. Client total: %.2f, Server calculated: %.2f.", clientGrandTotal, round(serverCalculatedGrandTotal)));
            }*/

            // E. Update all fields on the originalSale object with the new, re-calculated data.
            originalSale.setSaleDate(updatedHeader.getSaleDate());
            originalSale.setPaymentMode(updatedHeader.getPaymentMode());
            originalSale.setTransactionReference(updatedHeader.getTransactionReference());
            originalSale.setGstType(updatedHeader.getGstType());
            originalSale.setWalkInCustomerName(updatedHeader.getWalkInCustomerName());
            originalSale.setWalkInCustomerMobile(updatedHeader.getWalkInCustomerMobile());
            originalSale.setPatientId(updatedHeader.getPatientId());
            originalSale.setDoctorId(updatedHeader.getDoctorId());
            originalSale.setPrescriptionDate(updatedHeader.getPrescriptionDate());
            originalSale.setItems(newSaleItems);
            originalSale.setDoctorName(updatedHeader.getDoctorName());
            originalSale.setAddress(updatedHeader.getAddress());
            originalSale.setAge(updatedHeader.getAge());
            originalSale.setGender(updatedHeader.getGender());


            // Update all financial fields with new calculated values
            originalSale.setTotalMrpAmount(round(serverCalculatedTotalMrp));
            originalSale.setTotalDiscountAmount(round(serverCalculatedTotalDiscount));
            originalSale.setTotalTaxableAmount(round(serverCalculatedTotalTaxable));
            originalSale.setTotalTaxAmount(round(serverCalculatedTotalTax));
            originalSale.setOverallAdjustmentType(updatedHeader.getOverallAdjustmentType()); // <-- Store the new adjustment type
            originalSale.setOverallAdjustmentValue(updatedHeader.getOverallAdjustmentValue()); // <-- Store the new value
            originalSale.setCalculatedOverallAdjustmentAmount(round(calculatedOverallAdjustmentAmount)); // <-- Store the new calculated amount
            originalSale.setGrandTotal(round(serverCalculatedGrandTotal)); // <-- Store the final grand total

            // Add audit fields for update
            // originalSale.setUpdatedBy(updatedByUserId);
            // originalSale.setUpdatedAt(Timestamp.now());

            // F. Stage the final write to save the updated document.
            saleRepository.saveInTransaction(transaction, originalSale);
            return originalSale;
        }).get();
    }


    private Sale processSaleCreation(String orgId, String branchId, Sale partialSale, List<SaleItemDto> itemDtos, Double clientGrandTotal)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: ALL DATABASE READS AND DATA GATHERING
            // ===================================================================

            List<String> requiredMedicineIds = itemDtos.stream().map(SaleItemDto::getMedicineId).distinct().collect(Collectors.toList());

            List<String> requiredTaxProfileIds = itemDtos.stream()
                    .map(SaleItemDto::getTaxProfileId).distinct().collect(Collectors.toList());

            // Batch-read all required Medicine documents.
            Map<String, Medicine> medicineMasterDataMap = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIds)
                    .stream().map(doc -> doc.toObject(Medicine.class))
                    .collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            // Batch-read all required Tax Profile documents, but only if the sale is taxable.

            Map<String, TaxProfile> taxProfileMap = new HashMap<>();
            if (partialSale.getGstType() != GstType.NON_GST) {
                if (!requiredTaxProfileIds.isEmpty()) {
                    taxProfileMap.putAll(taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds)
                            .stream().map(doc -> doc.toObject(TaxProfile.class))
                            .collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity())));
                }
            }

            // Batch-read ALL available batches for ALL required medicines.
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

                // Financial Calculation
                BigDecimal quantity = new BigDecimal(quantityToSell);
                BigDecimal mrpPerItem = BigDecimal.valueOf(itemDto.getMrp());
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal lineItemGrossMrp = mrpPerItem.multiply(quantity);
                BigDecimal lineItemDiscountAmount = lineItemGrossMrp.multiply(discountPercent);
                BigDecimal lineItemNetAfterDiscount = lineItemGrossMrp.subtract(lineItemDiscountAmount);
                BigDecimal lineItemTaxableAmount;
                BigDecimal lineItemTaxAmount;
                TaxProfile taxProfile = null;

                if (partialSale.getGstType() == GstType.NON_GST) {
                    lineItemTaxableAmount = lineItemNetAfterDiscount;
                    lineItemTaxAmount = BigDecimal.ZERO;
                } else {
                    taxProfile = taxProfileMap.get(itemDto.getTaxProfileId());
                    if (taxProfile == null) {
                        throw new InvalidRequestException("Tax profile with ID '" + itemDto.getTaxProfileId() + "' not found or is invalid.");
                    }

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

                // FEFO Stock Deduction and Batch Allocation
                List<BatchAllocation> allocations = new ArrayList<>();
                int remainingQtyToSell = quantityToSell;
                for (MedicineBatch batch : availableBatches) {
                    if (remainingQtyToSell <= 0) break;
                    int qtyToTakeFromThisBatch = Math.min(remainingQtyToSell, batch.getQuantityAvailable());
                    medicineBatchRepository.updateStockInTransaction(transaction, orgId, branchId, medicineId, batch.getBatchId(), -qtyToTakeFromThisBatch);
                    allocations.add(BatchAllocation.builder().batchId(batch.getBatchId()).batchNo(batch.getBatchNo()).quantityTaken(qtyToTakeFromThisBatch).expiryDate(batch.getExpiryDate()).build());
                    remainingQtyToSell -= qtyToTakeFromThisBatch;
                    //newly added code
                    medicineRepository.updateStockInTransaction(
                            transaction, orgId, branchId, medicine.getMedicineId(), -qtyToTakeFromThisBatch
                    );
                }

                // Build the rich SaleItem model
                finalSaleItems.add(SaleItem.builder()
                        .medicineId(medicineId).quantity(quantityToSell).batchAllocations(allocations)
                        .mrpPerItem(mrpPerItem.doubleValue()).discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(lineItemDiscountAmount)).lineItemTaxableAmount(round(lineItemTaxableAmount))
                        .lineItemTotalAmount(round(lineItemTaxableAmount.add(lineItemTaxAmount)))
                        .taxProfileId(itemDto.getTaxProfileId())
                        .taxRateApplied(taxProfile != null ? taxProfile.getTotalRate() : 0.0)
                        .taxAmount(round(lineItemTaxAmount)).build());
            }

            // ===================================================================
            // PHASE 3: OVERALL ADJUSTMENT, FINAL VALIDATION & STAGING THE LAST WRITE
            // ===================================================================

            BigDecimal subTotal;
            if (partialSale.getGstType() == GstType.INCLUSIVE) {
                subTotal = serverCalculatedTotalMrp.subtract(serverCalculatedTotalDiscount);
            } else {
                subTotal = serverCalculatedTotalTaxable.add(serverCalculatedTotalTax);
            }

            BigDecimal calculatedOverallAdjustmentAmount = BigDecimal.ZERO;
            if (partialSale.getOverallAdjustmentType() != null && partialSale.getOverallAdjustmentValue() > 0) {
                BigDecimal adjustmentValue = BigDecimal.valueOf(partialSale.getOverallAdjustmentValue());
                switch (partialSale.getOverallAdjustmentType()) {
                    case PERCENTAGE_DISCOUNT:
                        calculatedOverallAdjustmentAmount = subTotal.multiply(adjustmentValue.divide(new BigDecimal(100)));
                        break;
                    case FIXED_DISCOUNT:
                        calculatedOverallAdjustmentAmount = adjustmentValue;
                        break;
                    case ADDITIONAL_CHARGE:
                        calculatedOverallAdjustmentAmount = adjustmentValue.negate();
                        break;
                }
            }

            BigDecimal serverCalculatedGrandTotal = subTotal.subtract(calculatedOverallAdjustmentAmount);

            double epsilon = 0.01;
           /* if (Math.abs(round(serverCalculatedGrandTotal) - clientGrandTotal) > epsilon) {
                throw new InvalidRequestException(String.format("Calculation mismatch error. Client total: %.2f, Server calculated total: %.2f.", clientGrandTotal, round(serverCalculatedGrandTotal)));
            }*/

            String saleId = IdGenerator.newId("sale");
            partialSale.setSaleId(saleId);
            partialSale.setTotalMrpAmount(round(serverCalculatedTotalMrp));
            partialSale.setTotalDiscountAmount(round(serverCalculatedTotalDiscount));
            partialSale.setTotalTaxableAmount(round(serverCalculatedTotalTaxable));
            partialSale.setTotalTaxAmount(round(serverCalculatedTotalTax));
            partialSale.setCalculatedOverallAdjustmentAmount(round(calculatedOverallAdjustmentAmount));
            partialSale.setGrandTotal(round(serverCalculatedGrandTotal));
            partialSale.setItems(finalSaleItems);

            saleRepository.saveInTransaction(transaction, partialSale);
            return partialSale;

        }).get();
    }
}


