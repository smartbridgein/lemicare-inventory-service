package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the request body of the 'Update Supplier' endpoint.
 * This class defines the fields of a supplier that are permissible to be updated.
 */
@Data
@NoArgsConstructor
public class UpdateSupplierRequest {

    /**
     * The updated name of the supplier. Required.
     */
    @NotBlank(message = "Supplier name is required.")
    private String name;

    /**
     * The updated contact person for the supplier. Optional.
     */
    private String contactPerson;

    /**
     * The updated mobile number for the supplier. Optional.
     */
    @NotBlank(message = "mobile number is required.")
    private String mobileNumber;

    /**
     * The updated email address for the supplier. Must be a valid format if provided.
     */
    @Email(message = "Please provide a valid email format.")
    private String email;

    /**
     * The updated physical address of the supplier. Optional.
     */
    private String address;

    private String status;
    @NotBlank(message = "GST Number is required.")
    private String gstin;

    private String drugLicenseNumber;

    // NOTE: We have intentionally OMITTED the 'gstin' field.
    // In this design, we are assuming that the GST number is a critical identifier
    // that should not be changed via a simple update endpoint. A more complex
    // business process (e.g., re-verification) might be required to change it.
    // If you decide 'gstin' should be updatable, you can simply add it here.
    // private String gstin;

}