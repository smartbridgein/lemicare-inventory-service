package com.cosmicdoc.inventoryservice.dto.request;

import com.cosmicdoc.common.model.AdjustmentType;
import com.cosmicdoc.common.model.GstType;
import com.cosmicdoc.common.model.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * DTO for updating an existing Prescription-based sale.
 * This class defines the complete new state of the sale invoice.
 */
@Data
public class UpdatePrescriptionSaleRequest {

    // --- Patient & Prescription Context ---
    @NotBlank(message = "Patient ID is required.")
    private String patientId;

    @NotBlank(message = "Doctor ID is required.")
    private String doctorId;

    @NotNull(message = "Prescription date is required.")
    private Date prescriptionDate;

    @NotNull(message = "Sale date is required.")
    private Date saleDate;

    // --- Payment Information ---
    @NotNull(message = "Payment mode is required.")
    private PaymentMode paymentMode;
    private String transactionReference; // Optional

    // --- Financial Summary Information ---
    @NotNull(message = "GST type is required.")
    private GstType gstType;

  //  @NotNull(message = "Grand total calculated by the client is required.")
    private Double grandTotal;

    // --- Line Items ---
    @Valid
    @NotEmpty(message = "A sale must contain at least one item.")
    private List<SaleItemDto> items;
    private String doctorName;
    private String address;
    private String gender;
    private int age;

    private AdjustmentType overallAdjustmentType; // Optional
    private Double overallAdjustmentValue;
}