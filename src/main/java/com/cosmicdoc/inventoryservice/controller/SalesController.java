package com.cosmicdoc.inventoryservice.controller;

import com.cosmicdoc.common.model.Sale;
import com.cosmicdoc.inventoryservice.dto.request.CreateOtcSaleRequest;
import com.cosmicdoc.inventoryservice.dto.request.CreatePrescriptionSaleRequest;
import com.cosmicdoc.inventoryservice.dto.request.UpdateOtcSaleRequest;
import com.cosmicdoc.inventoryservice.dto.request.UpdatePrescriptionSaleRequest;
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
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
public class SalesController {

    private final SalesService salesService;

    @PostMapping("/prescription")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
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

    /**
     * Endpoint to permanently delete a sale and restock the items.
     * This is a highly privileged and destructive operation.
     */
    @DeleteMapping("/{saleId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')") // Restrict to only the highest-level admin
    public ResponseEntity<Void> deleteSale(@PathVariable String saleId) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            salesService.deleteSale(orgId, branchId, saleId);
            // 204 No Content is the standard response for a successful DELETE.
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            // If the sale was already deleted, the goal is achieved.
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            // Log the exception in a real application
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Endpoint to update an existing Over-The-Counter (OTC) sale.
     *
     * @param saleId The ID of the sale to update, from the URL path.
     * @param request The request body containing the complete new state of the sale.
     * @return A ResponseEntity with the updated Sale object or an error.
     */
    @PutMapping("/otc/{saleId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> updateOtcSale(
            @PathVariable String saleId,
            @Valid @RequestBody UpdateOtcSaleRequest request) {

        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();

            Sale updatedSale = salesService.updateOtcSale(orgId, branchId, userId, saleId, request);

            return ResponseEntity.ok(updatedSale);

        } catch (IllegalStateException e) {
            // Catches business logic errors like "Stock already used".
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // In a real app, you would log this exception.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during the sale update.");
        }
    }

    @PutMapping("/prescription/{saleId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> updatePrescriptionSale(
            @PathVariable String saleId,
            @Valid @RequestBody UpdatePrescriptionSaleRequest request) {

        try {
            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            String userId = SecurityUtils.getUserId();

            Sale updatedSale = salesService.updatePrescriptionSale(orgId, branchId, userId, saleId, request);

            return ResponseEntity.ok(updatedSale);

        } catch (IllegalStateException e) {
            // Catches business logic errors like "Stock already used".
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // In a real app, you would log this exception.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during the sale update.");
        }
    }

}