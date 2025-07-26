package com.cosmicdoc.inventoryservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SupplierLedgerResponse {

    // --- Supplier Master Details ---
    private String supplierId;
    private String name;
    private String contactPerson;
    private String email;
    private String mobileNumber;

    // --- Financial Summary ---
    private double outstandingBalance;

    // --- Transaction History ---
    // This will be a list of purchases and returns
    private List<TransactionSummaryDto> transactions;

    // TODO: Add pagination info here (page number, total pages, etc.)
}