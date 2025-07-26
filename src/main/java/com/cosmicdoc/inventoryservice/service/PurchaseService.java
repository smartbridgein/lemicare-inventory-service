package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.*;
import com.cosmicdoc.common.repository.*;
import com.cosmicdoc.common.util.IdGenerator;
import com.cosmicdoc.inventoryservice.dto.request.CreatePurchaseRequest;
import com.cosmicdoc.inventoryservice.dto.request.UpdatePurchaseRequest;
import com.cosmicdoc.inventoryservice.dto.response.PurchaseDetailResponse;
import com.cosmicdoc.inventoryservice.exception.InvalidRequestException;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.cosmicdoc.common.model.AdjustmentType.*;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final Firestore firestore;
    private final PurchaseRepository purchaseRepository;
    private final MedicineRepository medicineRepository;
    private final TaxProfileRepository taxProfileRepository;
    private final SupplierRepository supplierRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    // You might also inject SupplierRepository to validate supplierId

    /*public Purchase createPurchase(String orgId, String branchId, String userId, CreatePurchaseRequest request)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: ALL READS & VALIDATION
            // ===================================================================

            // 1. Validate Supplier
            if (!supplierRepository.existsById(transaction, orgId, request.getSupplierId())) {
                throw new ResourceNotFoundException("Supplier with ID " + request.getSupplierId() + " not found.");
            }

            // 2. Batch-read all Medicine and Tax Profile master data upfront
            List<String> requiredMedicineIds = request.getItems().stream().map(CreatePurchaseRequest.PurchaseItemDto::getMedicineId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> medicineSnapshots = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIds);

            Map<String, Medicine> medicineMasterDataMap = medicineSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("Medicine with ID " + doc.getId() + " not found.");
                return doc.toObject(Medicine.class);
            }).collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            //List<String> requiredTaxProfileIds = medicineMasterDataMap.values().stream().map(Medicine::getTaxProfileId).distinct().collect(Collectors.toList());
            List<String> requiredTaxProfileIds = request.getItems().stream()
                    .map(CreatePurchaseRequest.PurchaseItemDto::getTaxProfileId)
                    .distinct()
                    .collect(Collectors.toList());
            List<DocumentSnapshot> taxProfileSnapshots = taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds);
            Map<String, TaxProfile> taxProfileMap = taxProfileSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("TaxProfile with ID " + doc.getId() + " not found.");
                return doc.toObject(TaxProfile.class);
            }).collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity()));

            // ===================================================================
            // PHASE 2: DATA PREPARATION & ADVANCED FINANCIAL CALCULATION
            // ===================================================================

            final BigDecimal[] invoiceTotalTaxable = {BigDecimal.ZERO};
            final BigDecimal[] invoiceTotalTax = {BigDecimal.ZERO};
            final BigDecimal[] invoiceTotalDiscount = {BigDecimal.ZERO};

            List<PurchaseItem> purchaseItems = request.getItems().stream().map(itemDto -> {
                Medicine masterMedicine = medicineMasterDataMap.get(itemDto.getMedicineId());
               // TaxProfile taxProfile = taxProfileMap.get(masterMedicine.getTaxProfileId());
                TaxProfile taxProfile = taxProfileMap.get(itemDto.getTaxProfileId());
                if (taxProfile == null) throw new InvalidRequestException("Tax profile for " + masterMedicine.getName() + " is missing.");

                // --- Convert DTO inputs to BigDecimal for precise calculations ---
                BigDecimal packQuantity = new BigDecimal(itemDto.getPackQuantity());
                BigDecimal costPerPack = BigDecimal.valueOf(itemDto.getPurchaseCostPerPack());
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal taxRate = BigDecimal.valueOf(taxProfile.getTotalRate()).divide(new BigDecimal(100));

                // --- Perform Line Item Financial Flow ---
                BigDecimal grossAmount = costPerPack.multiply(packQuantity);
                BigDecimal discountAmount = grossAmount.multiply(discountPercent);
                BigDecimal netAmountAfterDiscount = grossAmount.subtract(discountAmount);

                BigDecimal taxableAmount;
                BigDecimal taxAmount;

                // --- Handle Inclusive / Exclusive GST based on the invoice-level setting ---
                if (request.getGstType() == GstType.INCLUSIVE) {
                    taxableAmount = netAmountAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                    taxAmount = netAmountAfterDiscount.subtract(taxableAmount);
                } else { // EXCLUSIVE
                    taxableAmount = netAmountAfterDiscount;
                    taxAmount = taxableAmount.multiply(taxRate);
                }

                BigDecimal lineItemTotal = taxableAmount.add(taxAmount);

                // --- Aggregate totals for the main invoice document ---
                invoiceTotalTaxable[0] = invoiceTotalTaxable[0].add(taxableAmount);
                invoiceTotalTax[0] = invoiceTotalTax[0].add(taxAmount);
                invoiceTotalDiscount[0] = invoiceTotalDiscount[0].add(discountAmount);

                int totalUnitsReceived = (itemDto.getPackQuantity() + itemDto.getFreePackQuantity()) * itemDto.getItemsPerPack();

                // --- Build the rich PurchaseItem model for storage ---
                return PurchaseItem.builder()
                        .medicineId(itemDto.getMedicineId()).batchNo(itemDto.getBatchNo())
                        .expiryDate(Timestamp.of(itemDto.getExpiryDate()))
                        .packQuantity(itemDto.getPackQuantity()).freePackQuantity(itemDto.getFreePackQuantity())
                        .itemsPerPack(itemDto.getItemsPerPack()).totalReceivedQuantity(totalUnitsReceived)
                        .purchaseCostPerPack(itemDto.getPurchaseCostPerPack()).discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(discountAmount)).lineItemTaxableAmount(round(taxableAmount))
                        .lineItemTaxAmount(round(taxAmount)).lineItemTotalAmount(round(lineItemTotal))
                        .mrpPerItem(itemDto.getMrpPerItem()).taxProfileId(taxProfile.getTaxProfileId())
                        .taxRateApplied(taxProfile.getTotalRate()).taxComponents(taxProfile.getComponents()).build();
            }).collect(Collectors.toList());

           // BigDecimal grandTotal = invoiceTotalTaxable[0].add(invoiceTotalTax[0]);

            BigDecimal subTotalAfterLineItems = invoiceTotalTaxable[0].add(invoiceTotalTax[0]);
            BigDecimal calculatedOverallAdjustmentAmount = BigDecimal.ZERO;
            if (request.getOverallAdjustmentType() != null && request.getOverallAdjustmentValue() != null) {
                BigDecimal adjustmentValue = BigDecimal.valueOf(request.getOverallAdjustmentValue());

                switch (request.getOverallAdjustmentType()) {
                    case PERCENTAGE_DISCOUNT:
                        // Calculate percentage discount on the taxable amount
                        calculatedOverallAdjustmentAmount = invoiceTotalTaxable[0].multiply(adjustmentValue.divide(new BigDecimal(100)));
                        break;
                    case FIXED_DISCOUNT:
                        // The value is a direct monetary discount
                        calculatedOverallAdjustmentAmount = adjustmentValue;
                        break;
                    case ADDITIONAL_CHARGE:
                        // The value is a direct monetary charge, so we represent it as a negative "discount"
                        calculatedOverallAdjustmentAmount = adjustmentValue.negate();
                        break;
                }
            }

            // Grand Total is now the sub-total MINUS the overall adjustment/discount.
            // A negative discount (an additional charge) will correctly be added.
            BigDecimal finalGrandTotal = subTotalAfterLineItems.subtract(calculatedOverallAdjustmentAmount);
            //BigDecimal grandTotal = subTotalAfterLineDiscounts.subtract(calculatedOverallAdjustmentAmount);

            // ===================================================================
            // PHASE 3: ALL WRITES
            // ===================================================================
            // A. Determine payment status and due amount
            double amountPaid = request.getAmountPaid();
            double dueAmount = round(finalGrandTotal) - amountPaid;

            PaymentStatus paymentStatus;
            if (dueAmount <= 0.01) { // Use a small tolerance for floating point comparisons
                paymentStatus = PaymentStatus.PAID;
            } else if (amountPaid > 0) {
                paymentStatus = PaymentStatus.PARTIALLY_PAID;
            } else {
                paymentStatus = PaymentStatus.PENDING;
            }
            // B. Build the final Purchase domain object
            String purchaseId = "purchase_" + UUID.randomUUID().toString();
            Purchase newPurchase = Purchase.builder()
                    .purchaseId(purchaseId).organizationId(orgId).branchId(branchId)
                    .supplierId(request.getSupplierId()).invoiceDate(Timestamp.of(request.getInvoiceDate()))
                    .referenceId(request.getReferenceId()).gstType(request.getGstType())
                    .totalTaxableAmount(round(invoiceTotalTaxable[0]))
                    .totalDiscountAmount(round(invoiceTotalDiscount[0]))
                    .totalTaxAmount(round(invoiceTotalTax[0]))
                    .overallAdjustmentType(request.getOverallAdjustmentType())
                    .overallAdjustmentValue(request.getOverallAdjustmentValue() != null ? request.getOverallAdjustmentValue() : 0.0)
                    .calculatedOverallAdjustmentAmount(round(calculatedOverallAdjustmentAmount))
                    .totalAmount(round(finalGrandTotal))
                    .amountPaid(amountPaid) // <-- ADDED
                    .dueAmount(dueAmount)   // <-- ADDED
                    .paymentStatus(paymentStatus)
                    .items(purchaseItems) // The list of rich, calculated PurchaseItem objects
                    .createdBy(userId).createdAt(Timestamp.now()).build();

            // C. Prepare the initial Payment document (if any payment was made)
            SupplierPayment initialPayment = null;
            if (amountPaid > 0) {
                initialPayment = SupplierPayment.builder()
                        .paymentId("pay_" + UUID.randomUUID().toString())
                        .purchaseInvoiceId(purchaseId)
                        .paymentDate(Timestamp.of(request.getInvoiceDate()))
                        .amountPaid(amountPaid)
                        .paymentMode(request.getPaymentMode())
                        .referenceNumber(request.getPaymentReference())
                        .createdBy(userId).build();
            }

            // B. Queue the write for the new Purchase document
            purchaseRepository.saveInTransaction(transaction, newPurchase);

            if (initialPayment != null) {
                supplierPaymentRepository.saveInTransaction(transaction, orgId, request.getSupplierId(), initialPayment);
            }
                // 3. STAGE WRITE: Update the Supplier's outstanding balance.
                //    The balance increases by the amount that is *not* paid yet (the due amount).
                supplierRepository.updateBalanceInTransaction(transaction, orgId, request.getSupplierId(), dueAmount);

            // 4. Queue the creation of a new MedicineBatch document for each item

            for (PurchaseItem item : purchaseItems) {
                if (item.getTotalReceivedQuantity() > 0) {
                    MedicineBatch newBatch = MedicineBatch.builder()
                            .batchId(UUID.randomUUID().toString()).batchNo(item.getBatchNo())
                            .expiryDate(item.getExpiryDate()).quantityAvailable(item.getTotalReceivedQuantity())
                            .purchaseCost(item.getPurchaseCostPerPack() / item.getItemsPerPack()) // Store cost per single item
                            .mrp(item.getMrpPerItem()).build();
                    medicineBatchRepository.saveInTransaction(transaction, orgId, branchId, item.getMedicineId(), newBatch);
                }
            }

            DocumentReference supplierRef = firestore.collection("organizations").document(orgId)
                    .collection("suppliers").document(request.getSupplierId());

            // Use FieldValue.increment() to increase the balance.
            // This is safe from race conditions.
            transaction.update(supplierRef, "balance", FieldValue.increment(grandTotal.doubleValue()));

            return newPurchase;
        }).get();
    }*/

    public Purchase createPurchase(String orgId, String branchId, String userId, CreatePurchaseRequest request)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: ALL DATABASE READS & PRE-VALIDATION
            // ===================================================================

            // 1. Validate Supplier
            if (!supplierRepository.existsById(transaction, orgId, request.getSupplierId())) {
                throw new ResourceNotFoundException("Supplier with ID " + request.getSupplierId() + " not found.");
            }

            // 2. Batch-read all required Medicine and Tax Profile documents
            List<String> requiredMedicineIds = request.getItems().stream().map(CreatePurchaseRequest.PurchaseItemDto::getMedicineId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> medicineSnapshots = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIds);
            Map<String, Medicine> medicineMasterDataMap = medicineSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("Medicine with ID " + doc.getId() + " not found.");
                return doc.toObject(Medicine.class);
            }).collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            List<String> requiredTaxProfileIds = request.getItems().stream().map(CreatePurchaseRequest.PurchaseItemDto::getTaxProfileId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> taxProfileSnapshots = taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds);
            Map<String, TaxProfile> taxProfileMap = taxProfileSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("TaxProfile with ID " + doc.getId() + " not found.");
                return doc.toObject(TaxProfile.class);
            }).collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity()));

            // --- All database reads are now complete. ---


            // ===================================================================
            // PHASE 2: LINE ITEM & SUB-TOTAL CALCULATION
            // ===================================================================

            final BigDecimal[] invoiceTotalTaxable = {BigDecimal.ZERO};
            final BigDecimal[] invoiceTotalTax = {BigDecimal.ZERO};
            final BigDecimal[] invoiceTotalDiscount = {BigDecimal.ZERO};

            List<PurchaseItem> purchaseItems = request.getItems().stream().map(itemDto -> {
                Medicine masterMedicine = medicineMasterDataMap.get(itemDto.getMedicineId());
                TaxProfile taxProfile = taxProfileMap.get(itemDto.getTaxProfileId());
                if (taxProfile == null) throw new InvalidRequestException("Tax profile for " + masterMedicine.getName() + " is missing.");

                BigDecimal packQuantity = new BigDecimal(itemDto.getPackQuantity());
                BigDecimal costPerPack = BigDecimal.valueOf(itemDto.getPurchaseCostPerPack());
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal taxRate = BigDecimal.valueOf(taxProfile.getTotalRate()).divide(new BigDecimal(100));

                BigDecimal grossAmount = costPerPack.multiply(packQuantity);
                BigDecimal discountAmount = grossAmount.multiply(discountPercent);
                BigDecimal netAmountAfterDiscount = grossAmount.subtract(discountAmount);

                BigDecimal taxableAmount;
                BigDecimal taxAmount;
                if (request.getGstType() == GstType.INCLUSIVE) {
                    taxableAmount = netAmountAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                    taxAmount = netAmountAfterDiscount.subtract(taxableAmount);
                } else { // EXCLUSIVE
                    taxableAmount = netAmountAfterDiscount;
                    taxAmount = taxableAmount.multiply(taxRate);
                }

                invoiceTotalTaxable[0] = invoiceTotalTaxable[0].add(taxableAmount);
                invoiceTotalTax[0] = invoiceTotalTax[0].add(taxAmount);
                invoiceTotalDiscount[0] = invoiceTotalDiscount[0].add(discountAmount);

                int totalUnitsReceived = (itemDto.getPackQuantity() + itemDto.getFreePackQuantity()) * itemDto.getItemsPerPack();
                return PurchaseItem.builder()
                        .medicineId(itemDto.getMedicineId()).batchNo(itemDto.getBatchNo())
                        .expiryDate(Timestamp.of(itemDto.getExpiryDate()))
                        .packQuantity(itemDto.getPackQuantity()).freePackQuantity(itemDto.getFreePackQuantity())
                        .itemsPerPack(itemDto.getItemsPerPack()).totalReceivedQuantity(totalUnitsReceived)
                        .purchaseCostPerPack(itemDto.getPurchaseCostPerPack()).discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(discountAmount)).lineItemTaxableAmount(round(taxableAmount))
                        .lineItemTaxAmount(round(taxAmount)).lineItemTotalAmount(round(taxableAmount.add(taxAmount)))
                        .mrpPerItem(itemDto.getMrpPerItem()).taxProfileId(itemDto.getTaxProfileId())
                        .taxRateApplied(taxProfile.getTotalRate()).taxComponents(taxProfile.getComponents()).build();
            }).collect(Collectors.toList());

            // ===================================================================
            // PHASE 3: OVERALL ADJUSTMENT & FINAL CALCULATION
            // ===================================================================

            BigDecimal subTotal = invoiceTotalTaxable[0].add(invoiceTotalTax[0]);
            BigDecimal calculatedOverallAdjustmentAmount = BigDecimal.ZERO;

            if (request.getOverallAdjustmentType() != null && request.getOverallAdjustmentValue() != null) {
                BigDecimal adjustmentValue = BigDecimal.valueOf(request.getOverallAdjustmentValue());
                switch (request.getOverallAdjustmentType()) {
                    case PERCENTAGE_DISCOUNT:
                        calculatedOverallAdjustmentAmount = invoiceTotalTaxable[0].multiply(adjustmentValue.divide(new BigDecimal(100)));
                        break;
                    case FIXED_DISCOUNT:
                        calculatedOverallAdjustmentAmount = adjustmentValue;
                        break;
                    case ADDITIONAL_CHARGE:
                        calculatedOverallAdjustmentAmount = adjustmentValue.negate();
                        break;
                }
            }

            BigDecimal grandTotal = subTotal.subtract(calculatedOverallAdjustmentAmount);

            double amountPaid = request.getAmountPaid();
            double dueAmount = round(grandTotal) - amountPaid;
            PaymentStatus paymentStatus = (dueAmount <= 0.01) ? PaymentStatus.PAID : (amountPaid > 0 ? PaymentStatus.PARTIALLY_PAID : PaymentStatus.PENDING);

            // ===================================================================
            // PHASE 4: PREPARE AND STAGE ALL WRITES
            // ===================================================================

            String purchaseId = IdGenerator.newId("PUR");
            Purchase newPurchase = Purchase.builder()
                    .purchaseId(purchaseId).organizationId(orgId).branchId(branchId)
                    .supplierId(request.getSupplierId())
                    .supplierName(request.getSupplierName())
                    .invoiceDate(Timestamp.of(request.getInvoiceDate()))
                    .referenceId(request.getReferenceId()).gstType(request.getGstType())
                    .totalTaxableAmount(round(invoiceTotalTaxable[0]))
                    .totalDiscountAmount(round(invoiceTotalDiscount[0]))
                    .totalTaxAmount(round(invoiceTotalTax[0]))
                    .overallAdjustmentType(request.getOverallAdjustmentType())
                    .overallAdjustmentValue(request.getOverallAdjustmentValue() != null ? request.getOverallAdjustmentValue() : 0.0)
                    .calculatedOverallAdjustmentAmount(round(calculatedOverallAdjustmentAmount))
                    .totalAmount(round(grandTotal))
                    .amountPaid(amountPaid).dueAmount(dueAmount).paymentStatus(paymentStatus)
                    .items(purchaseItems).createdBy(userId).createdAt(Timestamp.now()).build();

            purchaseRepository.saveInTransaction(transaction, newPurchase);

            if (amountPaid > 0) {
                SupplierPayment initialPayment = SupplierPayment.builder()
                        .paymentId(IdGenerator.newId("PAY")).purchaseInvoiceId(purchaseId)
                        .paymentDate(Timestamp.of(request.getInvoiceDate())).amountPaid(amountPaid)
                        .paymentMode(request.getPaymentMode()).referenceNumber(request.getPaymentReference())
                        .createdBy(userId).build();
                supplierPaymentRepository.saveInTransaction(transaction, orgId, request.getSupplierId(), initialPayment);
            }

            supplierRepository.updateBalanceInTransaction(transaction, orgId, request.getSupplierId(), dueAmount);

            for (PurchaseItem item : purchaseItems) {
                if (item.getTotalReceivedQuantity() > 0) {
                    MedicineBatch newBatch = MedicineBatch.builder()
                            .batchId(IdGenerator.newId("")).batchNo(item.getBatchNo())
                            .expiryDate(item.getExpiryDate()).quantityAvailable(item.getTotalReceivedQuantity())
                            .purchaseCost(item.getPurchaseCostPerPack() / item.getItemsPerPack())
                            .mrp(item.getMrpPerItem()).build();
                    medicineBatchRepository.saveInTransaction(transaction, orgId, branchId, item.getMedicineId(), newBatch);
                }
                medicineRepository.updateStockInTransaction(
                        transaction, orgId, branchId, item.getMedicineId(), item.getTotalReceivedQuantity()
                );
            }

            return newPurchase;
        }).get();
    }

    private double round(BigDecimal value) {
        if (value == null) return 0.0;
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }


    public List<Purchase> getPurchasesForBranch(String orgId, String branchId) {
        return purchaseRepository.findAllByBranchId(orgId, branchId);
    }

    public PurchaseDetailResponse getPurchaseById(String orgId, String branchId, String purchaseId) {
        Purchase purchase = purchaseRepository.findById(orgId, branchId, purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase with ID " + purchaseId + " not found."));
        // In a real app, you would fetch medicine names here to enrich the response
        List<String> medicineIds = purchase.getItems().stream()
                .map(PurchaseItem::getMedicineId)
                .distinct()
                .collect(Collectors.toList());
        // 3. Fetch all the corresponding Medicine master documents in a single batch read.
        //    (This assumes your MedicineRepository has a 'findAllByIds' method).
        List<Medicine> medicines = medicineRepository.findAllByIds(orgId, branchId, medicineIds);
        // 4. Create the lookup map: medicineId -> medicineName.
        Map<String, String> medicineIdToNameMap = medicines.stream()
                .collect(Collectors.toMap(Medicine::getMedicineId, Medicine::getName));
        // 5. Call the DTO factory method, now passing the enrichment map.
        return PurchaseDetailResponse.from(purchase, medicineIdToNameMap);

    }

    /**
     * Updates an existing purchase invoice. This is a complex transactional operation
     * that reverses the old stock and financial impacts before applying the new ones.
     *
     * A critical precondition check ensures that a purchase cannot be edited if any
     * stock from its original batches has already been sold or returned.
     *
     * @param purchaseId The ID of the purchase to update.
     * @param request The DTO containing the full set of updated data for the invoice.
     * @return The updated Purchase object.
     */
    public Purchase updatePurchase(String orgId, String branchId, String userId, String purchaseId, UpdatePurchaseRequest request)
            throws ExecutionException, InterruptedException {

        return firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: READ ALL ORIGINAL & NEW DATA
            // ===================================================================

            // 1. READ the original Purchase document.
            Purchase originalPurchase = purchaseRepository.findById(transaction, orgId, branchId, purchaseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase with ID " + purchaseId + " not found to update."));

            // 2. READ all MedicineBatches created by the original purchase.
            Map<String, MedicineBatch> oldBatchesMap = new HashMap<>();
            for (PurchaseItem oldItem : originalPurchase.getItems()) {
                medicineBatchRepository.findByBatchNo(transaction, orgId, branchId, oldItem.getMedicineId(), oldItem.getBatchNo())
                        .ifPresent(batch -> oldBatchesMap.put(oldItem.getMedicineId() + "_" + oldItem.getBatchNo(), batch));
            }

            // 3. READ master data (Medicines, TaxProfiles) for the NEW request items.
            List<String> requiredMedicineIds = request.getItems().stream().map(UpdatePurchaseRequest.PurchaseItemDto::getMedicineId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> medicineSnapshots = medicineRepository.getAll(transaction, orgId, branchId, requiredMedicineIds);
            Map<String, Medicine> medicineMasterDataMap = medicineSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("Medicine with ID " + doc.getId() + " not found.");
                return doc.toObject(Medicine.class);
            }).collect(Collectors.toMap(Medicine::getMedicineId, Function.identity()));

            List<String> requiredTaxProfileIds = request.getItems().stream().map(UpdatePurchaseRequest.PurchaseItemDto::getTaxProfileId).distinct().collect(Collectors.toList());
            List<DocumentSnapshot> taxProfileSnapshots = taxProfileRepository.getAll(transaction, orgId, requiredTaxProfileIds);
            Map<String, TaxProfile> taxProfileMap = taxProfileSnapshots.stream().map(doc -> {
                if (!doc.exists()) throw new ResourceNotFoundException("TaxProfile with ID " + doc.getId() + " not found.");
                return doc.toObject(TaxProfile.class);
            }).collect(Collectors.toMap(TaxProfile::getTaxProfileId, Function.identity()));

            // ===================================================================
            // PHASE 2: VALIDATE, REVERSE, & RE-CALCULATE
            // ===================================================================

            // --- A. REVERSE the old inventory (Staging Writes) & VALIDATE stock usage ---
            for (PurchaseItem oldItem : originalPurchase.getItems()) {
                MedicineBatch oldBatch = oldBatchesMap.get(oldItem.getMedicineId() + "_" + oldItem.getBatchNo());
                if (oldBatch != null) {
                    if (oldBatch.getQuantityAvailable() < oldItem.getTotalReceivedQuantity()) {
                        throw new IllegalStateException("Cannot edit purchase. Stock from batch " + oldItem.getBatchNo() + " has already been used.");
                    }
                    medicineBatchRepository.updateStockInTransaction(transaction, orgId, branchId, oldItem.getMedicineId(), oldBatch.getBatchId(), -oldItem.getTotalReceivedQuantity());
                }
            }

            // --- B. RE-CALCULATE everything for the new purchase state (In-Memory) ---
            final BigDecimal[] newTotalTaxable = {BigDecimal.ZERO};
            final BigDecimal[] newTotalTax = {BigDecimal.ZERO};
            final BigDecimal[] newTotalDiscount = {BigDecimal.ZERO};

            List<PurchaseItem> newPurchaseItems = request.getItems().stream().map(itemDto -> {
                Medicine masterMedicine = medicineMasterDataMap.get(itemDto.getMedicineId());
                TaxProfile taxProfile = taxProfileMap.get(itemDto.getTaxProfileId());
                if (taxProfile == null) throw new InvalidRequestException("Tax profile for " + masterMedicine.getName() + " is missing.");

                BigDecimal packQuantity = new BigDecimal(itemDto.getPackQuantity());
                BigDecimal costPerPack = BigDecimal.valueOf(itemDto.getPurchaseCostPerPack());
                BigDecimal discountPercent = BigDecimal.valueOf(itemDto.getDiscountPercentage()).divide(new BigDecimal(100));
                BigDecimal taxRate = BigDecimal.valueOf(taxProfile.getTotalRate()).divide(new BigDecimal(100));

                BigDecimal grossAmount = costPerPack.multiply(packQuantity);
                BigDecimal discountAmount = grossAmount.multiply(discountPercent);
                BigDecimal netAmountAfterDiscount = grossAmount.subtract(discountAmount);

                BigDecimal taxableAmount;
                BigDecimal taxAmount;
                if (request.getGstType() == GstType.INCLUSIVE) {
                    taxableAmount = netAmountAfterDiscount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
                    taxAmount = netAmountAfterDiscount.subtract(taxableAmount);
                } else { // EXCLUSIVE
                    taxableAmount = netAmountAfterDiscount;
                    taxAmount = taxableAmount.multiply(taxRate);
                }

                newTotalTaxable[0] = newTotalTaxable[0].add(taxableAmount);
                newTotalTax[0] = newTotalTax[0].add(taxAmount);
                newTotalDiscount[0] = newTotalDiscount[0].add(discountAmount);

                int totalUnitsReceived = (itemDto.getPackQuantity() + itemDto.getFreePackQuantity()) * itemDto.getItemsPerPack();
                return PurchaseItem.builder()
                        .medicineId(itemDto.getMedicineId()).batchNo(itemDto.getBatchNo())
                        .expiryDate(Timestamp.of(itemDto.getExpiryDate()))
                        .packQuantity(itemDto.getPackQuantity()).freePackQuantity(itemDto.getFreePackQuantity())
                        .itemsPerPack(itemDto.getItemsPerPack()).totalReceivedQuantity(totalUnitsReceived)
                        .purchaseCostPerPack(itemDto.getPurchaseCostPerPack()).discountPercentage(itemDto.getDiscountPercentage())
                        .lineItemDiscountAmount(round(discountAmount)).lineItemTaxableAmount(round(taxableAmount))
                        .lineItemTaxAmount(round(taxAmount)).lineItemTotalAmount(round(taxableAmount.add(taxAmount)))
                        .mrpPerItem(itemDto.getMrpPerItem()).taxProfileId(itemDto.getTaxProfileId())
                        .taxRateApplied(taxProfile.getTotalRate()).taxComponents(taxProfile.getComponents()).build();
            }).collect(Collectors.toList());

            BigDecimal newGrandTotal = newTotalTaxable[0].add(newTotalTax[0]);
            double newAmountPaid = request.getAmountPaid();
            double newDueAmount = round(newGrandTotal) - newAmountPaid;

            // ===================================================================
            // PHASE 3: UPDATE MODEL & STAGE FINAL WRITES
            // ===================================================================

            // 1. UPDATE all fields on the originalPurchase object.
            originalPurchase.setSupplierId(request.getSupplierId());
            originalPurchase.setInvoiceDate(Timestamp.of(request.getInvoiceDate()));
            originalPurchase.setReferenceId(request.getReferenceId());
            originalPurchase.setGstType(request.getGstType());
            originalPurchase.setItems(newPurchaseItems);
            originalPurchase.setTotalTaxableAmount(round(newTotalTaxable[0]));
            originalPurchase.setTotalDiscountAmount(round(newTotalDiscount[0]));
            originalPurchase.setTotalTaxAmount(round(newTotalTax[0]));
            originalPurchase.setTotalAmount(round(newGrandTotal));
            originalPurchase.setAmountPaid(newAmountPaid);
            originalPurchase.setDueAmount(newDueAmount);
            originalPurchase.setPaymentStatus(newDueAmount <= 0.01 ? PaymentStatus.PAID : (newAmountPaid > 0 ? PaymentStatus.PARTIALLY_PAID : PaymentStatus.PENDING));
            // You can add 'updatedBy' and 'updatedAt' audit fields here

            // 2. STAGE WRITE: Save the updated Purchase document.
            purchaseRepository.saveInTransaction(transaction, originalPurchase);

            // 3. STAGE WRITE: Create new MedicineBatch documents for the updated purchase.
            for (PurchaseItem newItem : newPurchaseItems) {
                if (newItem.getTotalReceivedQuantity() > 0) {
                    MedicineBatch updatedBatch = MedicineBatch.builder()
                            .batchId(IdGenerator.newId("")).batchNo(newItem.getBatchNo())
                            .expiryDate(newItem.getExpiryDate()).quantityAvailable(newItem.getTotalReceivedQuantity())
                            .purchaseCost(newItem.getPurchaseCostPerPack() / newItem.getItemsPerPack())
                            .mrp(newItem.getMrpPerItem()).build();
                    medicineBatchRepository.saveInTransaction(transaction, orgId, branchId, newItem.getMedicineId(), updatedBatch);
                }
            }

            // 4. STAGE WRITE: Atomically update the supplier's balance.
            double oldDueAmount = originalPurchase.getDueAmount();
            double balanceChange = newDueAmount - oldDueAmount;
            supplierRepository.updateBalanceInTransaction(transaction, orgId, request.getSupplierId(), balanceChange);

            // Note: A full implementation would also reverse/re-apply payments.
            // For simplicity, we are assuming the payment is re-entered with the update.

            return originalPurchase;
        }).get();
    }


    /**
     * Performs a "hard delete" on a Purchase, permanently removing the invoice,
     * its associated payment records, and the stock it introduced.
     *
     * Includes a critical safety check to prevent deletion if any stock from the
     * purchase has already been sold or returned.
     */
    public void deletePurchase(String orgId, String branchId, String purchaseId)
            throws ExecutionException, InterruptedException {

        firestore.runTransaction(transaction -> {
            // ===================================================================
            // PHASE 1: READS & VALIDATION
            // ===================================================================

            // 1. READ the original Purchase document.
            Purchase purchaseToDelete = purchaseRepository.findById(transaction, orgId, branchId, purchaseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase with ID " + purchaseId + " not found."));

            // 2. READ all MedicineBatches created by this purchase to validate them.
            for (PurchaseItem item : purchaseToDelete.getItems()) {
                MedicineBatch batch = medicineBatchRepository
                        .findByBatchNo(transaction, orgId, branchId, item.getMedicineId(), item.getBatchNo())
                        .orElse(null); // It's okay if the batch was already deleted manually

                if (batch != null) {
                    // CRITICAL SAFETY CHECK
                    if (batch.getQuantityAvailable() < item.getTotalReceivedQuantity()) {
                        throw new IllegalStateException("Cannot delete purchase. Stock from batch " + item.getBatchNo() + " has already been used.");
                    }
                }
            }

            // ===================================================================
            // PHASE 2: STAGE ALL DELETE & UPDATE OPERATIONS
            // ===================================================================

            // 1. STAGE DELETE: Delete all MedicineBatches created by this purchase.
            for (PurchaseItem item : purchaseToDelete.getItems()) {
                medicineBatchRepository
                        .findByBatchNo(transaction, orgId, branchId, item.getMedicineId(), item.getBatchNo())
                        .ifPresent(batch -> {
                            medicineBatchRepository.deleteByIdInTransaction(transaction, orgId, branchId, item.getMedicineId(), batch.getBatchId());
                        });
            }

            // 2. STAGE DELETE: Delete all payment records associated with this purchase.
            supplierPaymentRepository.deleteAllByPurchaseIdInTransaction(transaction, orgId, purchaseToDelete.getSupplierId(), purchaseId);

            // 3. STAGE UPDATE: Reverse the financial impact on the supplier's balance.
            supplierRepository.updateBalanceInTransaction(transaction, orgId, purchaseToDelete.getSupplierId(), -purchaseToDelete.getDueAmount());

            // 4. STAGE DELETE: Delete the main Purchase document itself.
            purchaseRepository.deleteByIdInTransaction(transaction, orgId, branchId, purchaseId);

            return null; // Return null as this is a void operation
        }).get();
    }
}
