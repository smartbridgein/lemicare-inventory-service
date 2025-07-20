package com.cosmicdoc.inventoryservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DailySalesSummaryResponse {
    private String organizationId;
    private String branchId;
    private LocalDate date;
    private double totalSales;
    private int transactionCount;
}
