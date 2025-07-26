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
public class CreateOtcSaleRequest {
    private String patientName;
    private String patientMobile;
    private String orgId;

    @NotNull(message = "Payment mode is required.")
    private PaymentMode paymentMode;

    private String transactionReference;
    @NotNull
    private Date date;

    // --- NEW: Financial summary sent from the client for validation ---
    @NotNull
    private GstType gstType; // EXCLUSIVE or INCLUSIVE
    //@NotNull
    private Double subTotal; // Sum of MRP * Qty for all items
    //@NotNull
    private Double totalDiscount;
  //  @NotNull
    private Double totalTax;


    @NotNull(message = "Grand total calculated by the client is required.")
    private Double grandTotal; // <-- REQUIRED

    @Valid @NotEmpty
    private List<SaleItemDto> items;

    private AdjustmentType overallAdjustmentType; // Optional
    private Double overallAdjustmentValue;

}