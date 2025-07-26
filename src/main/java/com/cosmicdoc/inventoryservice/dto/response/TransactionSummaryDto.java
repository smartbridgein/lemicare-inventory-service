package com.cosmicdoc.inventoryservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Date;

@Data
@Builder
public class TransactionSummaryDto {
    private String transactionId; // e.g., purchaseId or purchaseReturnId
    private Date date;
    private String type; // "PURCHASE" or "PURCHASE_RETURN"
    private String referenceId; // The invoice number
    private double invoiceAmount; // The total value of the transaction
    private double amountPaid; // For purchases
    private double amountCredited; // For returns
}