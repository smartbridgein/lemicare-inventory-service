package com.cosmicdoc.inventoryservice.controller;

import com.cosmicdoc.common.model.Medicine;
import com.cosmicdoc.common.model.Supplier;
import com.cosmicdoc.common.model.TaxProfile;
import com.cosmicdoc.common.repository.MedicineRepository;
import com.cosmicdoc.inventoryservice.dto.request.*;
import com.cosmicdoc.inventoryservice.dto.response.MedicineStockResponse;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.cosmicdoc.inventoryservice.security.SecurityUtils;
import com.cosmicdoc.inventoryservice.service.MasterDataService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/masters")
@RequiredArgsConstructor
@Tag(name = "Master Data Management", description = "APIs for managing master records like Suppliers, Medicines, and Tax Profiles")
// This annotation applies the lock icon to all endpoints in this controller
@SecurityRequirement(name = "bearerAuth")
public class MasterDataController {

    private final MasterDataService masterDataService;


    @Operation(
            summary = "Create a new Supplier",
            description = "Creates a new supplier record for the authenticated organization. Requires ADMIN or SUPER_ADMIN role.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Supplier created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Supplier.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request body"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have required role")
            }
    )
    // --- Supplier Endpoints (Organization-Specific) ---
    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    //@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<Supplier> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        // Suppliers are at the organization level, so we only need the orgId.
        String orgId = SecurityUtils.getOrganizationId();
        Supplier newSupplier = masterDataService.createSupplier(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newSupplier);
    }

    @GetMapping("/suppliers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Supplier>> listSuppliers() {
        String orgId = SecurityUtils.getOrganizationId();
        List<Supplier> suppliers = masterDataService.getSuppliersForOrg(orgId);
        return ResponseEntity.ok(suppliers);
    }

    @PutMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> updateSupplier(
            @PathVariable String supplierId,
            @Valid @RequestBody UpdateSupplierRequest request) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            Supplier updatedSupplier = masterDataService.updateSupplier(orgId, supplierId, request);
            return ResponseEntity.ok(updatedSupplier);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> deleteSupplier(@PathVariable String supplierId) {
        String orgId = SecurityUtils.getOrganizationId();
        masterDataService.deleteSupplier(orgId, supplierId);
        return ResponseEntity.noContent().build();
    }

    // --- Tax Profile Endpoints (Organization-Specific) ---

    @GetMapping("/tax-profiles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaxProfile>> listTaxProfiles() {
        String orgId = SecurityUtils.getOrganizationId();
        List<TaxProfile> taxProfiles = masterDataService.getTaxProfilesForOrg(orgId);
        return ResponseEntity.ok(taxProfiles);
    }

    @PostMapping("/tax-profiles")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<TaxProfile> createTaxProfile(@Valid @RequestBody CreateTaxProfileRequest request) {
        // Tax Profiles are also at the organization level.
        String orgId = SecurityUtils.getOrganizationId();
        TaxProfile newTaxProfile = masterDataService.createTaxProfile(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newTaxProfile);
    }

    // --- Medicine Endpoints (Branch-Specific) ---

    @PostMapping("/medicines")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<Medicine> createMedicine(@Valid @RequestBody CreateMedicineRequest request) {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();
        Medicine newMedicine = masterDataService.createMedicine(orgId, branchId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newMedicine);
    }

    @GetMapping("/medicines")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MedicineStockResponse>> listMedicines() {
        String orgId = SecurityUtils.getOrganizationId();
        String branchId = SecurityUtils.getBranchId();
        // The service layer correctly returns the DTO, not the full model
        List<MedicineStockResponse> medicines = masterDataService.getMedicinesForBranch(orgId, branchId);
        return ResponseEntity.ok(medicines);
    }

    @GetMapping("/medicines/{medicineId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Medicine> getMedicineById(@PathVariable String medicineId) {

            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            Medicine medicine = masterDataService.getMedicineById(orgId, branchId, medicineId);
            return ResponseEntity.ok(medicine);
    }

    @PutMapping("/medicines/{medicineId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> updateMedicine(
            @PathVariable String medicineId,
            @Valid @RequestBody UpdateMedicineRequest request) {

            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            Medicine updatedMedicine = masterDataService.updateMedicine(orgId, branchId, medicineId, request);
            return ResponseEntity.ok(updatedMedicine);
    }

    @DeleteMapping("/medicines/{medicineId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> deleteMedicine(@PathVariable String medicineId) {

            String orgId = SecurityUtils.getOrganizationId();
            String branchId = SecurityUtils.getBranchId();
            masterDataService.deleteMedicine(orgId, branchId, medicineId);
            return ResponseEntity.noContent().build(); // 204 No Content is standard for successful deletes
    }

    @PutMapping("/tax-profiles/{taxProfileId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> updateTaxProfile(
            @PathVariable String taxProfileId,
            @Valid @RequestBody UpdateTaxProfileRequest request) {
       // try {
            String orgId = SecurityUtils.getOrganizationId();
            TaxProfile updatedProfile = masterDataService.updateTaxProfile(orgId, taxProfileId, request);
            return ResponseEntity.ok(updatedProfile);
        //} catch (ResourceNotFoundException e) {
           // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
       // } catch (IllegalArgumentException e) {
            // Catches the "name already exists" error
         //   return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }

    @DeleteMapping("/tax-profiles/{taxProfileId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> deleteTaxProfile(@PathVariable String taxProfileId) {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            masterDataService.deleteTaxProfile(orgId, taxProfileId);
            // 204 No Content is the standard, correct response for a successful DELETE.
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            // If it's already not found, the outcome is the same.
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            // This catches the "profile in use" error.
            // 409 Conflict is an appropriate status code.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @Operation(
            summary = "Clean up Tax Profiles",
            description = "Deletes all tax profiles except the 'no tax' profile. This is useful for resolving tax profile inconsistencies. Medicines using deleted profiles will be updated to use the 'no tax' profile. Requires ADMIN or SUPER_ADMIN role.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cleanup completed successfully",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have required role")
            }
    )
    @PostMapping("/tax-profiles/cleanup")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> cleanupTaxProfiles() {
        try {
            String orgId = SecurityUtils.getOrganizationId();
            MasterDataService.CleanupResult result = masterDataService.cleanupTaxProfiles(orgId);
            
            // Create response object
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("message", "Tax profile cleanup completed");
            response.put("deletedCount", result.getDeletedCount());
            response.put("errorCount", result.getErrorCount());
            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to cleanup tax profiles: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

 }



