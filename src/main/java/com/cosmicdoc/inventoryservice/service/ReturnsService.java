package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.*;
import com.cosmicdoc.common.repository.*;
import com.cosmicdoc.common.util.IdGenerator;
import com.cosmicdoc.inventoryservice.dto.request.CreatePurchaseReturnRequest;
import com.cosmicdoc.inventoryservice.dto.request.CreateSalesReturnRequest;
import com.cosmicdoc.inventoryservice.dto.response.PurchaseReturnListResponse;
import com.cosmicdoc.inventoryservice.dto.response.SalesReturnListResponse;
import com.cosmicdoc.inventoryservice.exception.InsufficientStockException;
import com.cosmicdoc.inventoryservice.exception.InvalidRequestException;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnsService {

    private final Firestore firestore;
    private final SalesReturnRepository salesReturnRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final MedicineRepository medicineRepository; // Needed for validation
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    /**
     * Processes a sales return from a patient. This operation is transactional to
     * validate the original sale and medicine, and to atomically create new batches
     * for the returned stock.
     */
    public SalesReturn processSalesReturn(String orgId, String branchId, String createdByUserId, CreateSalesReturnRequest request)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: READS & VALIDATION
            // ===================================================================

            // 1. READ the original Sale document. This is the source of truth.
            Sale originalSale = saleRepository.findById(transaction, orgId, branchId, request.getOriginalSaleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Original sale with ID " + request.getOriginalSaleId() + " not found."));

            // ===================================================================
            // PHASE 2: CALCULATIONS & PREPARING WRITES
            // ===================================================================

            BigDecimal invoiceTotalMrp = BigDecimal.ZERO;
            BigDecimal invoiceTotalDiscount = BigDecimal.ZERO;
            BigDecimal invoiceTotalTaxable = BigDecimal.ZERO;
            BigDecimal invoiceTotalTax = BigDecimal.ZERO;
            List<SalesReturnItem> returnItems = new ArrayList<>();

            for (var itemDto : request.getItems()) {
                if (itemDto.getReturnQuantity() <= 0) continue;

                // --- THIS IS THE CORRECTED LOGIC TO FIND THE ORIGINAL ITEM ---
                // A. Find the matching line item from the original sale by medicineId.
                //    We assume one medicine appears only once per sale invoice.
                SaleItem originalItem = originalSale.getItems().stream()
                        .filter(orig -> orig.getMedicineId().equals(itemDto.getMedicineId()))
                        .findFirst()
                        .orElseThrow(() -> new InvalidRequestException("Medicine with ID " + itemDto.getMedicineId() + " not found in original sale."));

                // B. Validate the return quantity against the originally purchased quantity.
                if (originalItem.getQuantity() < itemDto.getReturnQuantity()) {
                    throw new InvalidRequestException("Cannot return more than the " + originalItem.getQuantity() + " units purchased for medicine " + itemDto.getMedicineId());
                }

                // --- End of corrected logic ---

                // C. Calculate the credit value for this line item based on the original sale's prices.
                BigDecimal returnQuantity = new BigDecimal(itemDto.getReturnQuantity());
                BigDecimal mrpPerItem = BigDecimal.valueOf(originalItem.getMrpPerItem());
                BigDecimal discountPercent = BigDecimal.valueOf(originalItem.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal taxRate = BigDecimal.valueOf(originalItem.getTaxRateApplied()).divide(new BigDecimal(100));

                BigDecimal lineItemGrossMrp = mrpPerItem.multiply(returnQuantity);
                BigDecimal lineItemDiscountAmount = lineItemGrossMrp.multiply(discountPercent);
                BigDecimal lineItemNetAfterDiscount = lineItemGrossMrp.subtract(lineItemDiscountAmount);

// --- THIS IS THE CORRECTED LOGIC ---
// We assume sales prices (MRP) are always tax-inclusive.
                BigDecimal lineItemTaxableAmount = lineItemNetAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                BigDecimal lineItemTaxAmount = lineItemNetAfterDiscount.subtract(lineItemTaxableAmount); // <-- FIXED
// ------------------------------------

// D. Aggregate totals for the return invoice.
                invoiceTotalMrp = invoiceTotalMrp.add(lineItemGrossMrp);
                invoiceTotalDiscount = invoiceTotalDiscount.add(lineItemDiscountAmount);
                invoiceTotalTaxable = invoiceTotalTaxable.add(lineItemTaxableAmount);
                invoiceTotalTax = invoiceTotalTax.add(lineItemTaxAmount);

                // E. STAGE WRITE: Add stock back to inventory by creating a new batch.
                MedicineBatch returnedBatch = MedicineBatch.builder()
                        .batchId(IdGenerator.newId("batch")) // Use new ID generator
                        .batchNo("SRET-" + itemDto.getBatchNo())
                        .expiryDate(Timestamp.now())
                        .quantityAvailable(itemDto.getReturnQuantity())
                        .purchaseCost(0.0).mrp(0.0).build();
                medicineBatchRepository.saveInTransaction(transaction, orgId, branchId, itemDto.getMedicineId(), returnedBatch);

                // F. Build the rich SalesReturnItem model for storage.
                returnItems.add(SalesReturnItem.builder()
                        .medicineId(itemDto.getMedicineId())
                        .batchNo(itemDto.getBatchNo()) // The batch being returned
                        .returnQuantity(itemDto.getReturnQuantity())
                        .mrpAtTimeOfSale(originalItem.getMrpPerItem())
                        .discountPercentageAtSale(originalItem.getDiscountPercentage())
                        .lineItemReturnValue(round(lineItemNetAfterDiscount))
                        .lineItemTaxAmount(round(lineItemTaxAmount)).build());
            }

            // ===================================================================
            // PHASE 3: FINALIZE AND STAGE FINAL WRITE
            // ===================================================================
            BigDecimal overallDiscountPercent = BigDecimal.valueOf(request.getOverallDiscountPercentage()).divide(new BigDecimal(100));
            BigDecimal netTotalBeforeOverallDiscount = invoiceTotalMrp.subtract(invoiceTotalDiscount);
            BigDecimal overallDiscountAmount = netTotalBeforeOverallDiscount.multiply(overallDiscountPercent);
            BigDecimal finalRefundAmount = netTotalBeforeOverallDiscount.subtract(overallDiscountAmount);

            String returnId = IdGenerator.newId("SRET"); // Use new ID generator
            SalesReturn salesReturn = SalesReturn.builder()
                    .salesReturnId(returnId).organizationId(orgId).branchId(branchId)
                    .originalSaleId(request.getOriginalSaleId()).patientId(originalSale.getPatientId())
                    //.reason(request.getReason())
                    .returnDate(Timestamp.of(request.getReturnDate()))
                    .createdBy(createdByUserId)
                    .totalReturnedMrp(round(invoiceTotalMrp))
                    .totalReturnedDiscount(round(invoiceTotalDiscount))
                    .totalReturnedTaxable(round(invoiceTotalTaxable))
                    .totalReturnedTax(round(invoiceTotalTax))
                    .overallDiscountPercentage(request.getOverallDiscountPercentage())
                    .overallDiscountAmount(round(overallDiscountAmount))
                    .netRefundAmount(round(finalRefundAmount))
                    .refundMode(request.getRefundMode())
                    .refundReference(request.getRefundReference())
                    .items(returnItems).build();

            salesReturnRepository.saveInTransaction(transaction,orgId,branchId,salesReturn);
            return salesReturn;
        }).get();
    }

    /*public SalesReturn processSalesReturn(String orgId, String branchId, String createdByUserId, CreateSalesReturnRequest request)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: READS & VALIDATION
            // ===================================================================

            // 1. READ the original Sale document. This is the source of truth for all prices and discounts.
            Sale originalSale = saleRepository.findById(transaction, orgId, branchId, request.getOriginalSaleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Original sale with ID " + request.getOriginalSaleId() + " not found."));

            // ===================================================================
            // PHASE 2: CALCULATIONS & PREPARING WRITES
            // ===================================================================

            BigDecimal invoiceTotalMrp = BigDecimal.ZERO;
            BigDecimal invoiceTotalDiscount = BigDecimal.ZERO;
            BigDecimal invoiceTotalTaxable = BigDecimal.ZERO;
            BigDecimal invoiceTotalTax = BigDecimal.ZERO;

            List<SalesReturnItem> returnItems = new ArrayList<>();

            for (var itemDto : request.getItems()) {
                if (itemDto.getReturnQuantity() <= 0) continue;

                // A. Find the matching item from the original sale to get its financial details.
                SaleItem originalItem = originalSale.getItems().stream()
                        .filter(orig -> orig.getMedicineId().equals(itemDto.getMedicineId()) && orig.getBatchNo().equals(itemDto.getBatchNo()))
                        .findFirst()
                        .orElseThrow(() -> new InvalidRequestException("Item with batch " + itemDto.getBatchNo() + " not found in original sale."));

                if (originalItem.getQuantity() < itemDto.getReturnQuantity()){
                    throw new InvalidRequestException("Cannot return more than the quantity purchased for item " + originalItem.getMedicineId());
                }

                // B. Calculate the credit value for this line item based on the original sale's prices.
                BigDecimal returnQuantity = new BigDecimal(itemDto.getReturnQuantity());
                BigDecimal mrpPerItem = BigDecimal.valueOf(originalItem.getMrpPerItem());
                BigDecimal discountPercent = BigDecimal.valueOf(originalItem.getDiscountPercentage());
                BigDecimal taxRate = BigDecimal.valueOf(originalItem.getTaxRateApplied()).divide(new BigDecimal(100));

                BigDecimal lineItemGrossMrp = mrpPerItem.multiply(returnQuantity);
                BigDecimal lineItemDiscountAmount = lineItemGrossMrp.multiply(discountPercent);
                BigDecimal lineItemNetAfterDiscount = lineItemGrossMrp.subtract(lineItemDiscountAmount);

                BigDecimal lineItemTaxableAmount = lineItemNetAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                BigDecimal lineItemTaxAmount = lineItemNetAfterDiscount.subtract(lineItemTaxableAmount);

                // C. Aggregate totals for the return invoice.
                invoiceTotalMrp = invoiceTotalMrp.add(lineItemGrossMrp);
                invoiceTotalDiscount = invoiceTotalDiscount.add(lineItemDiscountAmount);
                invoiceTotalTaxable = invoiceTotalTaxable.add(lineItemTaxableAmount);
                invoiceTotalTax = invoiceTotalTax.add(lineItemTaxAmount);

                // D. STAGE WRITE: Add stock back to inventory by creating a new batch.
                MedicineBatch returnedBatch = MedicineBatch.builder()
                        .batchId(UUID.randomUUID().toString()).batchNo("SRET-" + itemDto.getBatchNo())
                        .expiryDate(Timestamp.now()).quantityAvailable(itemDto.getReturnQuantity())
                        .purchaseCost(0.0).mrp(0.0).build();
                medicineBatchRepository.saveInTransaction(transaction, orgId, branchId, itemDto.getMedicineId(), returnedBatch);

                // E. Build the rich SalesReturnItem model for storage.
                returnItems.add(SalesReturnItem.builder()

                        .medicineId(itemDto.getMedicineId()).batchNo(itemDto.getBatchNo())
                        .returnQuantity(itemDto.getReturnQuantity()).mrpAtTimeOfSale(originalItem.getMrpPerItem())
                        .discountPercentageAtSale(originalItem.getDiscountPercentage()).lineItemReturnValue(round(lineItemNetAfterDiscount))
                        .lineItemTaxAmount(round(lineItemTaxAmount)).build());
            }

            // ===================================================================
            // PHASE 3: FINALIZE AND STAGE FINAL WRITE
            // ===================================================================
            BigDecimal overallDiscountPercent = BigDecimal.valueOf(request.getOverallDiscountPercentage()).divide(new BigDecimal(100));
            BigDecimal netTotalBeforeOverallDiscount = invoiceTotalMrp.subtract(invoiceTotalDiscount);
            BigDecimal overallDiscountAmount = netTotalBeforeOverallDiscount.multiply(overallDiscountPercent);
            BigDecimal finalRefundAmount = netTotalBeforeOverallDiscount.subtract(overallDiscountAmount);

            String returnId = IdGenerator.newId("SRET");
            SalesReturn salesReturn = SalesReturn.builder()
                    .salesReturnId(returnId).organizationId(orgId).branchId(branchId)
                    .originalSaleId(request.getOriginalSaleId()).patientId(originalSale.getPatientId())
                    //.reason(request.getReason())
                    .returnDate(Timestamp.of(request.getReturnDate()))
                    .createdBy(createdByUserId)
                    .totalReturnedMrp(round(invoiceTotalMrp))
                    .totalReturnedDiscount(round(invoiceTotalDiscount))
                    .totalReturnedTaxable(round(invoiceTotalTaxable))
                    .totalReturnedTax(round(invoiceTotalTax))
                    .overallDiscountPercentage(request.getOverallDiscountPercentage())
                    .overallDiscountAmount(round(overallDiscountAmount))
                    .netRefundAmount(round(finalRefundAmount))
                    .refundMode(request.getRefundMode()) // <-- ADD THIS
                    .refundReference(request.getRefundReference())
                    .items(returnItems).build();

            salesReturnRepository.saveInTransaction(transaction,orgId,branchId, salesReturn);
            return salesReturn;
        }).get();
    }
*/
    /**
     * Processes a purchase return to a supplier. This is a "read-modify-write" operation,
     * so a Transaction is required to ensure data integrity.
     */
    public PurchaseReturn processPurchaseReturn(String orgId, String branchId, String createdByUserId, CreatePurchaseReturnRequest request)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: ALL READS & VALIDATION
            // ===================================================================

            // 1. READ the original Purchase document. This is the source of truth.
            Purchase originalPurchase = purchaseRepository.findById(transaction, orgId, branchId, request.getOriginalPurchaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Original purchase with ID " + request.getOriginalPurchaseId() + " not found."));

            Supplier supplier = supplierRepository.findById(transaction, orgId, originalPurchase.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier with ID " + originalPurchase.getSupplierId() + " not found."));
            List<PurchaseReturnItem> returnItems = new ArrayList<>();
            BigDecimal totalReturnValue = BigDecimal.ZERO;

            for (var itemDto : request.getItems()) {
                if (itemDto.getReturnQuantity() <= 0) continue;

                // 2. READ the specific batch document to validate stock.
                MedicineBatch batchToReturn = medicineBatchRepository
                        .findByBatchNo(transaction, orgId, branchId, itemDto.getMedicineId(), itemDto.getBatchNo())
                        .orElseThrow(() -> new ResourceNotFoundException("Batch " + itemDto.getBatchNo() + " not found for medicine ID: " + itemDto.getMedicineId()));

                // 3. VALIDATE stock in that specific batch.
                if (batchToReturn.getQuantityAvailable() < itemDto.getReturnQuantity()) {
                    throw new InsufficientStockException("Cannot return more stock than available in batch " + itemDto.getBatchNo());
                }

                // ===================================================================
                // PHASE 2: CALCULATIONS & PREPARING WRITES
                // ===================================================================

                // A. Find the matching item from the original purchase to get its cost.
                PurchaseItem originalItem = originalPurchase.getItems().stream()
                        .filter(pItem -> pItem.getMedicineId().equals(itemDto.getMedicineId()) && pItem.getBatchNo().equals(itemDto.getBatchNo()))
                        .findFirst()
                        .orElseThrow(() -> new InvalidRequestException("Item with batch " + itemDto.getBatchNo() + " not found in original purchase."));

                // B. Calculate the value of the returned goods for this line item.
                // We use the taxable cost per item from the original purchase.
                double taxableCostPerUnit = originalItem.getLineItemTaxableAmount() / originalItem.getTotalReceivedQuantity();
                BigDecimal returnValue = BigDecimal.valueOf(taxableCostPerUnit).multiply(new BigDecimal(itemDto.getReturnQuantity()));
                totalReturnValue = totalReturnValue.add(returnValue);

                // C. STAGE WRITE: Decrement stock from the specific batch.
                medicineBatchRepository.updateStockInTransaction(transaction, orgId, branchId, itemDto.getMedicineId(), batchToReturn.getBatchId(), -itemDto.getReturnQuantity());

                // D. Build the rich PurchaseReturnItem model.
                returnItems.add(PurchaseReturnItem.builder()
                        .medicineId(itemDto.getMedicineId())
                        .batchNo(itemDto.getBatchNo())
                        .returnQuantity(itemDto.getReturnQuantity())
                        .costAtTimeOfPurchase(taxableCostPerUnit)
                        .lineItemReturnValue(round(returnValue))
                        .build());
            }

            // ===================================================================
            // PHASE 3: FINALIZE AND STAGE THE LAST WRITE
            // ===================================================================
            String returnId = IdGenerator.newId("PRET");
            PurchaseReturn purchaseReturn = PurchaseReturn.builder()
                    .purchaseReturnId(returnId).organizationId(orgId).branchId(branchId)
                    .originalPurchaseId(request.getOriginalPurchaseId()).supplierId(originalPurchase.getSupplierId())
                    .supplierName(supplier.getName())
                    .reason(request.getReason()).returnDate(Timestamp.of(request.getReturnDate()))
                    .createdBy(createdByUserId)
                    .totalReturnedAmount(round(totalReturnValue))
                    .items(returnItems)
                    .build();

            purchaseReturnRepository.saveInTransaction(transaction, orgId, branchId,purchaseReturn);

            DocumentReference supplierRef = firestore.collection("organizations").document(orgId)
                    .collection("suppliers").document(request.getSupplierId());

            // Use a negative value with FieldValue.increment() to decrease the balance.
            transaction.update(supplierRef, "balance", FieldValue.increment(-totalReturnValue.doubleValue()));
            return purchaseReturn;
        }).get();
    }

    private double round(BigDecimal value) {
        if (value == null) {
            return 0.0;
        }
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Get all returns (both sales and purchase returns)
     */
    public List<?> getAllReturns(String orgId, String branchId) throws ExecutionException, InterruptedException {
        // Fetch both sales and purchase returns and combine them
        List<SalesReturnListResponse> salesReturns = getSalesReturns(orgId, branchId);
        List<PurchaseReturnListResponse> purchaseReturns = getPurchaseReturns(orgId, branchId);

        // Combine the two lists
        List<Object> allReturns = new ArrayList<>();
        allReturns.addAll(salesReturns);
        allReturns.addAll(purchaseReturns);

        return allReturns;
    }

    /**
     * Get all sales returns
     */
    public List<SalesReturnListResponse> getSalesReturns(String orgId, String branchId) {
        // 1. The repository layer still fetches the full domain models.
        List<SalesReturn> salesReturns = salesReturnRepository.findAllByBranchId(orgId, branchId);

        // 2. The service layer is responsible for mapping the models to the response DTOs.
        return salesReturns.stream()
                .map(SalesReturnListResponse::from) // Use the static factory method on the DTO
                .collect(Collectors.toList());
    }

    /**
     * Get all purchase returns
     */
   /* public List<PurchaseReturn> getPurchaseReturns(String orgId, String branchId) throws ExecutionException, InterruptedException {
        try {
            QuerySnapshot querySnapshot = firestore.collection("organizations").document(orgId)
                    .collection("branches").document(branchId)
                    .collection("purchasereturns")
                    .orderBy("returnDate", Query.Direction.DESCENDING)
                    .get()
                    .get();

            return querySnapshot.getDocuments().stream()
                    .map(doc -> doc.toObject(PurchaseReturn.class))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching purchase returns", e);
            throw e;
        }
    }
*/

    public List<PurchaseReturnListResponse> getPurchaseReturns(String orgId, String branchId) {
        // The repository method still returns the full domain models
        List<PurchaseReturn> purchaseReturns = purchaseReturnRepository.findAllByBranchId(orgId, branchId);

        // The service layer is responsible for mapping the models to the response DTOs
        return purchaseReturns.stream()
                .map(PurchaseReturnListResponse::from) // Use the static factory method
                .collect(Collectors.toList());
    }


}
