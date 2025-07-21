package com.cosmicdoc.inventoryservice.dto.request;

import com.cosmicdoc.common.model.GstType;
import com.cosmicdoc.common.model.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class UpdateOtcSaleRequest {

    // --- Customer & Date Information ---
    private String patientName;
    private String patientMobile;
    @NotNull(message = "Sale date is required.")
    private Date date;

    // --- Payment Information ---
    @NotNull(message = "Payment mode is required.")
    private PaymentMode paymentMode;
    private String transactionReference; // Optional

    // --- Financial Summary Information ---
    @NotNull(message = "GST type is required.")
    private GstType gstType;

    //@NotNull(message = "Grand total calculated by the client is required.")
    private Double grandTotal;

    // --- Line Items ---
    @Valid
    @NotEmpty(message = "A sale must contain at least one item.")
    private List<SaleItemDto> items;
}
