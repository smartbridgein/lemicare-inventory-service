package com.cosmicdoc.inventoryservice.controller;

import com.cosmicdoc.common.model.PurchaseReturn;
import com.cosmicdoc.common.model.SalesReturn;
import com.cosmicdoc.inventoryservice.dto.request.CreatePurchaseReturnRequest;
import com.cosmicdoc.inventoryservice.dto.request.CreateSalesReturnRequest;
import com.cosmicdoc.inventoryservice.dto.response.PurchaseReturnListResponse;
import com.cosmicdoc.inventoryservice.dto.response.SalesReturnListResponse;
import com.cosmicdoc.inventoryservice.exception.InsufficientStockException;
import com.cosmicdoc.inventoryservice.security.SecurityUtils;
import com.cosmicdoc.inventoryservice.service.ReturnsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/inventory/returns")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')") // All return operations require authentication
public class ReturnsController {

    private final ReturnsService returnsService;
    
    /**
     * Get all returns (both sales and purchase returns)
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<?>> getAllReturns() {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            
            List<?> returns = returnsService.getAllReturns(orgId, branchId);
            return ResponseEntity.ok(returns);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null);
        }
    }
    
    /**
     * Get all sales returns
     */
    @GetMapping("/sales")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<SalesReturnListResponse>> getSalesReturns() {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();

        // This now correctly calls the service method that returns a list of DTOs
        List<SalesReturnListResponse> salesReturns = returnsService.getSalesReturns(orgId, branchId);

        return ResponseEntity.ok(salesReturns);
    }
    
    /**
     * Get all purchase returns
     */
    @GetMapping("/purchases")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<PurchaseReturnListResponse>> getPurchaseReturns() {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();

        // This now correctly calls the service method that returns a list of DTOs
        List<PurchaseReturnListResponse> purchaseReturns = returnsService.getPurchaseReturns(orgId, branchId);

        return ResponseEntity.ok(purchaseReturns);
    }

    /**
     * Endpoint to record a sales return from a patient.
     * This action increases stock levels.
     */
    @PostMapping("/sale")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> createSalesReturn(@Valid @RequestBody CreateSalesReturnRequest request) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();

            SalesReturn newReturn = returnsService.processSalesReturn(orgId, branchId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newReturn);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing sales return: " + e.getMessage());
        }
    }

    /**
     * Endpoint to record a purchase return to a supplier.
     * This action decreases stock levels.
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')") // Only admins can return to supplier
    public ResponseEntity<?> createPurchaseReturn(@Valid @RequestBody CreatePurchaseReturnRequest request) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();

            PurchaseReturn newReturn = returnsService.processPurchaseReturn(orgId, branchId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newReturn);
        } catch (InsufficientStockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing purchase return: " + e.getMessage());
        }
    }

 }