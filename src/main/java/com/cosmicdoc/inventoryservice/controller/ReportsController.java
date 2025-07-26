package com.cosmicdoc.inventoryservice.controller;

import com.cosmicdoc.inventoryservice.dto.response.DailySalesSummaryResponse;
import com.cosmicdoc.inventoryservice.dto.response.StockByCategoryResponse;
import com.cosmicdoc.inventoryservice.dto.response.SupplierLedgerResponse;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.cosmicdoc.inventoryservice.security.SecurityUtils;
import com.cosmicdoc.inventoryservice.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
public class ReportsController {

    private final ReportingService reportingService;

    @GetMapping("/stock-by-category")
    public ResponseEntity<List<StockByCategoryResponse>> getStockByCategory() {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();
        List<StockByCategoryResponse> report = reportingService.getStockByCategory(orgId, branchId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/daily-sales")
    public ResponseEntity<DailySalesSummaryResponse> getDailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();
        DailySalesSummaryResponse report = reportingService.getDailySalesSummary(orgId, branchId, date);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<?> getSupplierLedgerDetails(@PathVariable String supplierId) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            // Assuming ledger is branch-specific, but it could also be org-wide.
            String branchId = SecurityUtils.getBranchId();

            SupplierLedgerResponse ledger = reportingService.getSupplierLedger(orgId, branchId, supplierId);
            return ResponseEntity.ok(ledger);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}