package com.cosmicdoc.inventoryservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
@Data
@NoArgsConstructor
public class TaxComponentRequest {

    @NotBlank
    private String componentName; // Matches your JSON
    @NotNull
    private Double rate;
}
