package com.cosmicdoc.inventoryservice.controller;

import com.cosmicdoc.common.model.Purchase;
import com.cosmicdoc.inventoryservice.dto.request.CreatePurchaseRequest;
import com.cosmicdoc.inventoryservice.dto.response.PurchaseDetailResponse;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.cosmicdoc.inventoryservice.security.SecurityUtils;
import com.cosmicdoc.inventoryservice.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/purchases")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')") // Securing the whole controller
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/")
    public ResponseEntity<?> createPurchase(@Valid @RequestBody CreatePurchaseRequest request) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();
            Purchase newPurchase = purchaseService.createPurchase(orgId, branchId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newPurchase);
        } catch (ResourceNotFoundException e) {
            // If the service throws this specific exception...
            // ...return a 404 Not Found status with the error message.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // If the service throws any other unexpected exception...
            // ...return a 500 Internal Server Error.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/")
    public ResponseEntity<List<Purchase>> listPurchases() {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();
        List<Purchase> purchases = purchaseService.getPurchasesForBranch(orgId, branchId);
        return ResponseEntity.ok(purchases);
    }

    @GetMapping("/{purchaseId}")
    public ResponseEntity<?> getPurchaseDetails(@PathVariable String purchaseId) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            PurchaseDetailResponse purchaseDetails = purchaseService.getPurchaseById(orgId, branchId, purchaseId);
            return ResponseEntity.ok(purchaseDetails);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}