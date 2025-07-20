package com.cosmicdoc.inventoryservice.dto.request;

import com.cosmicdoc.common.model.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class CreateSalesReturnRequest {
    @NotBlank(message = "Original Sale ID is required for traceability.")
    private String originalSaleId;


    private Double refundAmount;

    private String reason;

    private Date returnDate;

    @NotNull @Min(0)
    private Double overallDiscountPercentage;

    @NotNull(message = "Refund mode is required.")
    private PaymentMode refundMode;

    private String refundReference; // Optional

    @Valid
    @NotEmpty(message = "A sales return must contain at least one item.")
    private List<ReturnItemDto> items;

    @Data
    public static class ReturnItemDto {
        @NotBlank(message = "Medicine ID is required.")
        private String medicineId;

        // Batch number is crucial to know which batch is being returned to stock.
        @NotBlank(message = "Batch number is required.")
        private String batchNo;

        @Min(value = 1, message = "Quantity must be at least 1.")
        private Integer returnQuantity;

        private Date expiryDate;
    }
}