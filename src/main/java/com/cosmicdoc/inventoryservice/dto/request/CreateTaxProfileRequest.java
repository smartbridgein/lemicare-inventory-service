package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO for the request body of the 'Create Tax Profile' endpoint.
 * Defines a complete tax structure, including its name, total rate, and components.
 */
@Data
@NoArgsConstructor
public class CreateTaxProfileRequest {

    @NotBlank(message = "Profile name is required (e.g., 'GST 18%').")
    private String profileName;

    @NotNull(message = "Total rate is required.")
    @PositiveOrZero(message = "Total rate must be zero or positive.")
    private Double totalRate;

    /**
     * A list of tax components that make up the total rate (e.g., CGST + SGST).
     * The list cannot be empty.
     * The @Valid annotation ensures that the validation rules inside the TaxComponent
     * class itself are also checked.
     */
    @Valid
    @NotEmpty(message = "A tax profile must have at least one component.")
    private List<TaxComponentRequest> components;
}