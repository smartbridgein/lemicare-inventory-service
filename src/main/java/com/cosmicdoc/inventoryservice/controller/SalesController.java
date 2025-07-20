package com.cosmicdoc.inventoryservice.controller;

import com.cosmicdoc.common.model.Sale;
import com.cosmicdoc.inventoryservice.dto.request.CreateOtcSaleRequest;
import com.cosmicdoc.inventoryservice.dto.request.CreatePrescriptionSaleRequest;
import com.cosmicdoc.inventoryservice.exception.InsufficientStockException;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.cosmicdoc.inventoryservice.security.SecurityUtils;
import com.cosmicdoc.inventoryservice.service.SalesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/inventory/sales")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // All sales operations require an authenticated user
public class SalesController {

    private final SalesService salesService;

    @PostMapping("/prescription")
    public ResponseEntity<?> createPrescriptionSale(@Valid @RequestBody CreatePrescriptionSaleRequest request) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();
            Sale newSale = salesService.createPrescriptionSale(orgId, branchId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newSale);
        } catch (InsufficientStockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while processing the sale.");
        }
    }

    @PostMapping("/otc")
    public ResponseEntity<?> createOtcSale(@Valid @RequestBody CreateOtcSaleRequest request) throws ExecutionException, InterruptedException {
        //try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();
            Sale newSale = salesService.createOtcSale(orgId, branchId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newSale);
        /*} catch (InsufficientStockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while processing the sale.");
        }*/
    }

    @GetMapping("/")
    public ResponseEntity<List<Sale>> listSales() {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();
        List<Sale> sales = salesService.getSalesForBranch(orgId, branchId);
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/{saleId}")
    public ResponseEntity<?> getSaleDetails(@PathVariable String saleId) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            Sale sale = salesService.getSaleById(orgId, branchId, saleId);
            return ResponseEntity.ok(sale);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}