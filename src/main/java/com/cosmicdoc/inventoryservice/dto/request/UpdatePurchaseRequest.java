package com.cosmicdoc.inventoryservice.dto.request;

import com.cosmicdoc.common.model.AdjustmentType;
import com.cosmicdoc.common.model.GstType;
import com.cosmicdoc.common.model.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * DTO for updating an existing Purchase Invoice.
 * It contains the complete set of data for the new state of the invoice.
 * The structure is identical to the CreatePurchaseRequest.
 */
@Data
public class UpdatePurchaseRequest {

    // --- Header Level Information ---
    @NotBlank(message = "Supplier ID is required.")
    private String supplierId;

    @NotNull(message = "Invoice date is required.")
    private Date invoiceDate;

    @NotBlank(message = "Reference ID / Invoice Number is required.")
    private String referenceId;

    @NotNull(message = "GST type (Exclusive/Inclusive) is required.")
    private GstType gstType;

    // --- Payment Information ---
    @NotNull(message = "Amount paid is required.")
    @Min(0)
    private Double amountPaid;

    @NotNull(message = "Payment mode is required.")
    private PaymentMode paymentMode;

    private String paymentReference; // Optional

    // --- Line Items ---
    @Valid // This is crucial to trigger validation on the nested objects
    @NotEmpty(message = "A purchase must contain at least one item.")
    private List<PurchaseItemDto> items;

    private AdjustmentType overallAdjustmentType; // Optional
    private Double overallAdjustmentValue; // Optional (e.g., 5 for 5%, or 50 for 50 rupees)


    /**
     * Nested DTO for each line item in the update request.
     */
    @Data
    public static class PurchaseItemDto {
        @NotBlank(message = "Medicine ID is required.")
        private String medicineId;

        @NotBlank(message = "Batch number is required.")
        private String batchNo;

        @NotNull(message = "Expiry date is required.")
        private Date expiryDate;

        @NotNull(message = "Pack quantity is required.")
        @Min(0)
        private Integer packQuantity;

        @NotNull(message = "Free pack quantity is required.")
        @Min(0)
        private Integer freePackQuantity;

        @NotNull(message = "Items per pack is required.")
        @Min(1)
        private Integer itemsPerPack;

        @NotNull(message = "Purchase cost per pack is required.")
        @PositiveOrZero
        private Double purchaseCostPerPack;

      //  @NotNull(message = "Discount percentage is required.")
      //  @Min(0)
        private Double discountPercentage;

        @NotNull(message = "MRP per item is required.")
        @PositiveOrZero
        private Double mrpPerItem;

      //  @NotBlank(message = "Tax Profile ID is required for each item.")
        private String taxProfileId;
    }
}