package com.cosmicdoc.inventoryservice.dto.response;

import com.cosmicdoc.common.model.PurchaseReturn;
import lombok.Builder;
import lombok.Data;
import java.util.Date;

@Data
@Builder
public class PurchaseReturnListResponse {
    private String purchaseReturnId;
    private String originalPurchaseId;
    private String supplierId; // You would fetch the supplier name in a real UI
    private String supplierName;
    private Date returnDate;
    private double totalReturnedAmount;

    /**
     * A static factory method to map the domain model to this list DTO.
     */
    public static PurchaseReturnListResponse from(PurchaseReturn purchaseReturn) {
        return PurchaseReturnListResponse.builder()
                .purchaseReturnId(purchaseReturn.getPurchaseReturnId())
                .originalPurchaseId(purchaseReturn.getOriginalPurchaseId())
                .supplierId(purchaseReturn.getSupplierId())
                //.supplierName(purchaseReturn.)
                .returnDate(purchaseReturn.getReturnDate().toDate())
                .totalReturnedAmount(purchaseReturn.getTotalReturnedAmount())
                .build();
    }
}