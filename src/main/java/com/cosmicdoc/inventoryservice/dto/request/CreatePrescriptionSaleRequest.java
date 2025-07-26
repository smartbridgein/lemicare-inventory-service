package com.cosmicdoc.inventoryservice.dto.request;

import com.cosmicdoc.common.model.AdjustmentType;
import com.cosmicdoc.common.model.GstType;
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
public class CreatePrescriptionSaleRequest {
    @NotBlank(message = "Patient ID is required.")
    private String patientId;

    @NotBlank(message = "Doctor ID is required.")
    private String doctorId;

    @NotNull
    private Date prescriptionDate;

    @NotNull
    private Date saleDate;
    // You could add an optional 'consultationId' here for better traceability

    @NotNull(message = "Payment mode is required.")
    private PaymentMode paymentMode;

    // Optional field for card transaction ID, UPI reference, etc.
    private String transactionReference;
    @NotNull(message = "Grand total calculated by the client is required.")
    private Double grandTotal; // <-- REQUIRED
    @NotNull
    private GstType gstType; // EXCLUSIVE or INCLUSIVE
    @Valid
    @NotEmpty(message = "A sale must contain at least one item.")
    private List<SaleItemDto> items;

    private AdjustmentType overallAdjustmentType; // Optional
    private Double overallAdjustmentValue;

 }