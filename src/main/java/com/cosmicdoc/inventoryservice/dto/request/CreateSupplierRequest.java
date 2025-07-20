package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for creating a new Supplier.
 * This class defines the API contract and validation rules based on the
 * "Add New Supplier" UI form.
 */
@Data
public class CreateSupplierRequest {

    @NotBlank(message = "Supplier Name is required.")
    private String name;

    @NotBlank(message = "Contact Number is required.")
    private String mobileNumber;

    //@NotBlank(message = "Contact Person is required.")
    private String contactPerson;

    //@NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email format.")
    private String email;

  //  @NotBlank(message = "Address is required.")
    private String address;

    @NotBlank(message = "GST Number is required.")
    private String gstin;

   // @NotBlank(message = "Drug License Number is required.")
    private String drugLicenseNumber; // <-- NEW FIELD

    private String status;
}