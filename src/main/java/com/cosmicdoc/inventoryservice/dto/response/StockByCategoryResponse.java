package com.cosmicdoc.inventoryservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockByCategoryResponse {
    private String category;
    private int totalStock;
}
