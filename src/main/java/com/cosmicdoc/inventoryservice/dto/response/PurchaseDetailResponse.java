package com.cosmicdoc.inventoryservice.dto.response;

import com.cosmicdoc.common.model.*;
import lombok.Builder;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A DTO representing the full, detailed view of a Purchase record.
 * This is a read-only object designed to be sent to the client.
 */
@Data
@Builder
public class PurchaseDetailResponse {
    // --- Header Information ---
    private String purchaseId;
    private String supplierId;
    private String supplierName;
    // TODO: Add supplierName for better UI display (requires fetching Supplier master data)
    private Date invoiceDate;
    private String referenceId;

    // --- Financial Summary ---
    private GstType gstType; // e.g., EXCLUSIVE, INCLUSIVE
    private double totalTaxableAmount;
    private double totalDiscountAmount;
    private double totalTaxAmount;
    private double totalAmount; // Grand Total

    // --- Line Items ---
    private List<PurchaseItemDetail> items;

    // --- Audit Information ---
    private String createdBy;
    private Date createdAt;
    private double amountPaid;
    private double dueAmount;
    private PaymentStatus paymentStatus;
    private AdjustmentType overallAdjustmentType;
    private double overallAdjustmentValue;


    /**
     * The nested DTO for each line item in the detailed purchase response.
     */
    @Data
    @Builder
    public static class PurchaseItemDetail {
        // --- Item Identification ---
        private String medicineId;
        private String medicineName; // Denormalized name for easy display
        private String batchNo;
        private Date expiryDate;

        // --- Quantity Details ---
        private int packQuantity;
        private int freePackQuantity;
        private int itemsPerPack;
        private int totalReceivedQuantity;

        // --- Financial Details for this Line Item ---
        private double purchaseCostPerPack;
        private double discountPercentage;
        private double lineItemDiscountAmount;
        private double lineItemTaxableAmount;
        private double lineItemTaxAmount;
        private double lineItemTotalAmount;
        private double mrpPerItem;

        // --- Tax Details for this Line Item ---
        private String taxProfileId;
        private double taxRateApplied;
        private List<TaxComponent> taxComponents;
    }

    /**
     * A static factory method to map a Purchase domain model to this detailed DTO.
     * Requires a pre-fetched map to enrich the response with medicine names.
     */
    public static PurchaseDetailResponse from(Purchase purchase, Map<String, String> medicineIdToNameMap) {
        // --- THIS IS THE CORRECTED MAPPING LOGIC ---
        return PurchaseDetailResponse.builder()
                // Map header fields
                .purchaseId(purchase.getPurchaseId())
                .supplierId(purchase.getSupplierId())
                .supplierName(purchase.getSupplierName())
                .invoiceDate(purchase.getInvoiceDate().toDate())
                .referenceId(purchase.getReferenceId())
                // Map financial summary
                .gstType(purchase.getGstType())
                .amountPaid(purchase.getAmountPaid())
                .dueAmount(purchase.getDueAmount())
                .paymentStatus(purchase.getPaymentStatus())
                .overallAdjustmentValue(purchase.getOverallAdjustmentValue())
                .totalTaxableAmount(purchase.getTotalTaxableAmount())
                .totalDiscountAmount(purchase.getTotalDiscountAmount())
                .totalTaxAmount(purchase.getTotalTaxAmount())
                .totalAmount(purchase.getTotalAmount())
                // Map audit fields
                .createdBy(purchase.getCreatedBy())
                .createdAt(purchase.getCreatedAt().toDate())
                // Map line items
                .items(purchase.getItems().stream().map(item ->
                        PurchaseItemDetail.builder()
                                .medicineId(item.getMedicineId())
                                .medicineName(medicineIdToNameMap.getOrDefault(item.getMedicineId(), "Unknown Medicine"))
                                .batchNo(item.getBatchNo())
                                .expiryDate(item.getExpiryDate().toDate())
                                .packQuantity(item.getPackQuantity())
                                .freePackQuantity(item.getFreePackQuantity())
                                .itemsPerPack(item.getItemsPerPack())
                                .totalReceivedQuantity(item.getTotalReceivedQuantity())
                                .purchaseCostPerPack(item.getPurchaseCostPerPack())
                                .discountPercentage(item.getDiscountPercentage())
                                .lineItemDiscountAmount(item.getLineItemDiscountAmount())
                                .lineItemTaxableAmount(item.getLineItemTaxableAmount())
                                .lineItemTaxAmount(item.getLineItemTaxAmount())
                                .lineItemTotalAmount(item.getLineItemTotalAmount())
                                .mrpPerItem(item.getMrpPerItem())
                                .taxProfileId(item.getTaxProfileId())
                                .taxRateApplied(item.getTaxRateApplied())
                                .taxComponents(item.getTaxComponents())
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }
}