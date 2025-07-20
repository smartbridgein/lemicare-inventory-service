package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class CreatePurchaseReturnRequest {
    @NotBlank(message = "Original Purchase ID is required.")
    private String originalPurchaseId;

    @NotBlank(message = "Supplier ID is required.")
    private String supplierId;

    private String reason;

    @Valid
    @NotEmpty(message = "A purchase return must contain at least one item.")
    private List<CreateSalesReturnRequest.ReturnItemDto> items; // Can reuse the same item DTO

    @NotNull
    private Date returnDate;
}