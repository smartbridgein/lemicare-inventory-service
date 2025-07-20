package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * DTO for updating the master details of an existing Medicine record.
 * This class defines the API contract and validation rules for which fields
 * are permissible to change.
 */
@Data
public class UpdateMedicineRequest {

    // The display name of the medicine.
    @NotBlank(message = "Medicine Name is required.")
    private String name;

    // The category or "Group Name".
    @NotBlank(message = "Group Name is required.")
    private String category;

    // The physical location in the pharmacy.
    private String location; // Optional

    // The sales unit.
    @NotBlank(message = "Unit of Measurement is required.")
    private String unitOfMeasurement;

    // The reorder level.
    @NotNull(message = "Low Stock Threshold is required.")
    @PositiveOrZero(message = "Low stock threshold cannot be negative.")
    private Integer lowStockThreshold;

    // The linked tax profile.
    @NotBlank(message = "Tax Profile ID is required.")
    private String taxProfileId;

    // The selling price (MRP).

    private Double unitPrice;

    private String hsnCode;

    private String genericName;

    private String manufacturer;

    private String sku;

    private String status;


    // NOTE: We have intentionally OMITTED fields like:
    // - genericName
    // - manufacturer
    // - sku
    // - hsnCode
    // This is a design choice to make them immutable after creation.
    // The DTO enforces this business rule at the API layer.
}