package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * DTO for creating a new master Medicine record.
 * This class defines the API contract and validation rules based on the
 * "Add New Medicine" UI form.
 */
@Data
public class CreateMedicineRequest {

    // Corresponds to "Medicine Name*"
    @NotBlank(message = "Medicine Name is required.")
    private String name;

    // Corresponds to "Group Name*"
    @NotBlank(message = "Group Name is required.")
    private String category;

    // Corresponds to "Generic*"
    @NotBlank(message = "Generic name is required.")
    private String genericName;

    // Corresponds to "Company*"
    @NotBlank(message = "Company/Manufacturer name is required.")
    private String manufacturer;

    // Corresponds to "Unit of Measurement*"
    @NotBlank(message = "Unit of Measurement is required.")
    private String unitOfMeasurement; // e.g., "Strip", "Bottle"

    // Corresponds to "Low Stock Threshold*"
    @NotNull(message = "Low Stock Threshold is required.")
    @PositiveOrZero(message = "Low stock threshold cannot be negative.")
    private Integer lowStockThreshold;

    // Corresponds to "Tax Profile*"
    @NotBlank(message = "Tax Profile ID is required.")
    private String taxProfileId;

    // --- Optional fields from the UI (no validation needed unless you want to add it) ---
    private String location;

    // --- Other relevant fields that should be part of the creation process ---

    // It's good practice to require a unit price (MRP) on creation.

    private Double unitPrice;

    // Optional fields for compliance and tracking.
    private String sku;
    private String hsnCode;
    private String status;
}