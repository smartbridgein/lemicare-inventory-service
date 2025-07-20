package com.cosmicdoc.inventoryservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import java.util.List;

/**
 * DTO for updating an existing Tax Profile.
 * The contract is currently identical to creating one, but having a separate
 * class provides future flexibility.
 */
@Data
public class UpdateTaxProfileRequest {

    @NotBlank(message = "Profile name is required.")
    private String profileName;

    @NotNull(message = "Total rate is required.")
    @PositiveOrZero(message = "Total rate must be zero or positive.")
    private Double totalRate;

    @Valid
    @NotEmpty(message = "A tax profile must have at least one component.")
    private List<TaxComponentRequest> components;
}
