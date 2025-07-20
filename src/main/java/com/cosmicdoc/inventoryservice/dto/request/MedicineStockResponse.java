package com.cosmicdoc.inventoryservice.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MedicineStockResponse {

    private String medicineId;

    // Fields from the UI table
    private String medicineName;
    private String genericName;
    private String group; // Corresponds to medicine.getCategory()
    private String mfg; // Corresponds to medicine.getManufacturer()
    private String location;
    private int availableStock; // The calculated real-time stock

    // You could also add other fields if the "view details" action needs them
}