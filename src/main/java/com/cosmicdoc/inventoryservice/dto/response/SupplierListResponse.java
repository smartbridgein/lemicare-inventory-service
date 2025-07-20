package com.cosmicdoc.inventoryservice.dto.response;

import com.cosmicdoc.common.model.Supplier;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupplierListResponse {
    private String supplierId;
    private String supplierName;
    private String gstNo;
    private String contactNo;
    private double balance; // The calculated balance
    private String status;

    public static SupplierListResponse from(Supplier supplier) {
        return SupplierListResponse.builder()
                .supplierId(supplier.getSupplierId())
                .supplierName(supplier.getName())
                .gstNo(supplier.getGstin())
                .contactNo(supplier.getMobileNumber())
                .balance(supplier.getBalance()) // Map the new field
                .status(supplier.getStatus())
                .build();
    }
}
