package com.cosmicdoc.inventoryservice.dto.response;

import com.cosmicdoc.common.model.Sale;
import lombok.Builder;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
        private List<BatchDetail> batches;
    }

    @Data
    @Builder
    public static class BatchDetail {
        private String batchNo;
        private Date expiryDate;
        private int quantity;
    }

    public static SaleDetailResponse from(Sale sale, Map<String, String> medicineIdToNameMap) {
        return SaleDetailResponse.builder()
                // ... map other fields ...
                .items(sale.getItems().stream()
                        .map(item -> {
                            // --- NEW MAPPING LOGIC ---
                            // For each SaleItem, create a list of its batch details.
                            List<BatchDetail> batchDetails = item.getBatchAllocations().stream()
                                    .map(alloc -> BatchDetail.builder()
                                            .batchNo(alloc.getBatchNo())
                                            .expiryDate(alloc.getExpiryDate().toDate())
                                            .quantity(alloc.getQuantityTaken())
                                            .build())
                                    .collect(Collectors.toList());

                            return SaleItemDetail.builder()
                                    .medicineId(item.getMedicineId())
                                    .medicineName(medicineIdToNameMap.get(item.getMedicineId()))
                                    .quantity(item.getQuantity())
                                    .batches(batchDetails) // <-- Use the new list
                                    .salePrice(item.getLineItemTotalAmount())
                                    .discountAmount(item.getLineItemDiscountAmount())
                                    .taxAmount(item.getTaxAmount())
                                    .build();
                        })
                        .collect(Collectors.toList()))
                .build();
    }
}