package com.cosmicdoc.inventoryservice.dto.response;

import com.cosmicdoc.common.model.SalesReturn;
import lombok.Builder;
import lombok.Data;
import java.util.Date;

/**
 * A DTO representing a summary of a Sales Return for list views.
 */
@Data
@Builder
public class SalesReturnListResponse {

    private String salesReturnId;
    private String originalSaleId;
    private String patientId; // It's useful to know which patient it was for
    private Date returnDate;
    private double netRefundAmount; // The final amount refunded to the customer

    /**
     * A static factory method to map a SalesReturn domain model to this list DTO.
     */
    public static SalesReturnListResponse from(SalesReturn salesReturn) {
        return SalesReturnListResponse.builder()
                .salesReturnId(salesReturn.getSalesReturnId())
                .originalSaleId(salesReturn.getOriginalSaleId())
                .patientId(salesReturn.getPatientId())
                .returnDate(salesReturn.getReturnDate().toDate())
                .netRefundAmount(salesReturn.getNetRefundAmount())
                .build();
    }
}