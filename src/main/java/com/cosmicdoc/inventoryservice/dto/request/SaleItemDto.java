package com.cosmicdoc.inventoryservice.dto.request;

import com.google.cloud.Timestamp;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

 @Data
 public  class SaleItemDto {
        @NotBlank
        private String medicineId;
        @NotNull
        @Min(1)
        private Integer quantity;
        @NotNull @Min(0)
        private Double discountPercentage;
        private String batchNumber;
        private Timestamp expiryDate;

     // The client sends the Unit MRP it used for calculation
        @NotNull @PositiveOrZero
        private Double mrp;
      @NotBlank(message = "Tax Profile ID is required for each sale item.")
        private String taxProfileId;
 }

