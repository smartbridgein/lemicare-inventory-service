package com.cosmicdoc.inventoryservice.dto.response;

import com.cosmicdoc.common.model.Sale;
import lombok.Builder;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class SaleDetailResponse {
    private String saleId;
    private String saleType;
    private Date saleDate;
    private String patientId;
    private String doctorId;
    private String walkInCustomerName;
    private String walkInCustomerMobile;
    private double totalTaxableAmount;
    private double totalTaxAmount;
    private double grandTotal;
    private String createdBy;
    private List<SaleItemDetail> items;

    @Data
    @Builder
    public static class SaleItemDetail {
        private String medicineId;
        private String medicineName;
        private String batchNo;
        private int quantity;
        private double salePrice;
        private double discountAmount;
        private double taxAmount;
    }

    public static SaleDetailResponse from(Sale sale) {
        return SaleDetailResponse.builder()
                .saleId(sale.getSaleId())
                // ... map all other fields from the Sale model ...
                .items(sale.getItems().stream()
                        .map(item -> SaleItemDetail.builder()
                                .medicineId(item.getMedicineId())
                                // .medicineName(...) would be enriched by the service
                                .batchNo(item.getBatchNo())
                                .quantity(item.getQuantity())
                                .salePrice(item.getSalePrice())
                                .discountAmount(item.getDiscountAmount())
                                .taxAmount(item.getTaxAmount())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}