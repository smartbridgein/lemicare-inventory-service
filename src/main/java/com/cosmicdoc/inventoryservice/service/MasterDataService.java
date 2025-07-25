package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.*;
import com.cosmicdoc.common.repository.MedicineBatchRepository;
import com.cosmicdoc.common.repository.MedicineRepository;
import com.cosmicdoc.common.repository.SupplierRepository;
import com.cosmicdoc.common.repository.TaxProfileRepository;
import com.cosmicdoc.common.util.IdGenerator;
import com.cosmicdoc.inventoryservice.dto.request.*;
import com.cosmicdoc.inventoryservice.dto.response.MedicineStockDetailResponse;
import com.cosmicdoc.inventoryservice.dto.response.MedicineStockResponse;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import com.cosmicdoc.inventoryservice.security.SecurityUtils;
import com.google.cloud.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private final MedicineRepository medicineRepository;
    private final SupplierRepository supplierRepository;
    private final TaxProfileRepository taxProfileRepository;
    private final MedicineBatchRepository medicineBatchRepository;

   /**
         * Creates a new master Medicine record for a specific branch.
         * This method maps the incoming DTO to the domain model and sets
         * server-controlled default values.
         *
         * @param orgId The organization ID from the security context.
         * @param branchId The branch ID from the security context.
         * @param dto The validated request DTO containing the new medicine's details.
         * @return The newly created and saved Medicine object.
         */
        public Medicine createMedicine(String orgId, String branchId, CreateMedicineRequest dto) {

            // --- THIS IS THE CORRECTED LOGIC ---

            // Check for duplicate medicine name in the same branch
            String medicineName = dto.getName();
            boolean medicineExists = medicineRepository.findAllByBranchId(orgId, branchId).stream()
                    .anyMatch(m -> m.getName().equalsIgnoreCase(medicineName));
                    
            if (medicineExists) {
                throw new IllegalArgumentException("A medicine with the name '" + medicineName + "' already exists in this branch.");
            }
            
            // 1. Generate a unique, server-side ID for the new medicine.
            String medicineId =  IdGenerator.newId("MED");;

            // 2. Build the Medicine domain object from the DTO, including the new fields.
            Medicine newMedicine = Medicine.builder()
                    .medicineId(medicineId)
                    .name(dto.getName())
                    .genericName(dto.getGenericName())   // <-- ADDED
                    .category(dto.getCategory())
                    .manufacturer(dto.getManufacturer()) // <-- ADDED
                    .unitOfMeasurement(dto.getUnitOfMeasurement())
                    .lowStockThreshold(dto.getLowStockThreshold())
                    .taxProfileId(dto.getTaxProfileId())
                    .unitPrice(dto.getUnitPrice())
                    // Optional fields
                    .location(dto.getLocation())         // <-- ADDED
                    .sku(dto.getSku())
                    .hsnCode(dto.getHsnCode())
                    // Server-controlled fields
                    .status(dto.getStatus()) // <-- Set default status on the server, not from the client.
                    .build();

            // 3. Save the new medicine to the database using the repository.
            return medicineRepository.save(orgId, branchId, newMedicine);
        }

    /*public List<MedicineStockResponse> getMedicinesForBranch(String orgId, String branchId) {
        // 1. Fetch all the medicine master documents for the branch.
        List<Medicine> medicines = medicineRepository.findAllByBranchId(orgId, branchId);

        // 2. For each medicine, calculate its total stock by summing its batches.
        return medicines.stream()
                .map(medicine -> {
                    // This assumes findAvailableBatches doesn't require a transaction for reads.
                    // If it does, you'll need a non-transactional version.
                    List<MedicineBatch> batches = medicineBatchRepository.findAllBatchesForMedicine(orgId, branchId, medicine.getMedicineId());

                    int totalStock = batches.stream()
                            .mapToInt(MedicineBatch::getQuantityAvailable)
                            .sum();

                    // 3. Create the response DTO with the calculated stock.
                    return MedicineStockResponse.from(medicine, totalStock);
                })
                .collect(Collectors.toList());
    }*/

    public List<MedicineStockResponse> getMedicinesForBranch(String orgId, String branchId) {
        // --- THE NEW, EFFICIENT LOGIC ---

        // 1. Fetch all the medicine master documents for the branch. This is the ONLY database call.
        List<Medicine> medicines = medicineRepository.findAllByBranchId(orgId, branchId);

        // 2. The stock is already on the object. Just map to the DTO.

        return medicines.stream()
                .map(medicine -> {
                    return MedicineStockResponse.from(medicine);
                })
                .collect(Collectors.toList());
    }

    public Medicine getMedicineById(String orgId, String branchId, String medicineId) {
        return medicineRepository.findById(orgId, branchId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine with ID " + medicineId + " not found."));


    }

    public List<Supplier> getSuppliersForOrg(String orgId) {
        return supplierRepository.findAllByOrganizationId(orgId);
    }

    public List<TaxProfile> getTaxProfilesForOrg(String orgId) {
        return taxProfileRepository.findAllByOrganizationId(orgId);
    }

    // --- ADD SUPPLIER LOGIC ---
    public Supplier createSupplier(String orgId, CreateSupplierRequest dto) {
        // Check for duplicate supplier name
        String supplierName = dto.getName();
        boolean supplierExists = supplierRepository.findAllByOrganizationId(orgId).stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(supplierName));
                
        if (supplierExists) {
            throw new IllegalArgumentException("A supplier with the name '" + supplierName + "' already exists.");
        }
        String supplierId = IdGenerator.newId("SUP");
        Supplier supplier = Supplier.builder()
                .supplierId(supplierId)
                .name(dto.getName())
                .gstin(dto.getGstin())
                .contactPerson(dto.getContactPerson())
                .mobileNumber(dto.getMobileNumber())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .createdBy(SecurityUtils.getUserId()) // Audit field
                .createdAt(Timestamp.now())
                .drugLicenseNumber(dto.getDrugLicenseNumber())
                .status(dto.getStatus())
                .balance(0.0)
                .build();
        return supplierRepository.save(orgId, supplier);
    }

    // --- ADD TAX PROFILE LOGIC ---
    public TaxProfile createTaxProfile(String orgId, CreateTaxProfileRequest dto) {

        // Manually map the list of TaxComponentDto to a list of TaxComponent models.
        List<TaxComponent> componentModels = dto.getComponents().stream()
                .map(componentDto -> TaxComponent.builder()
                        .name(componentDto.getComponentName()) // Map from dto.componentName to model.name
                        .rate(componentDto.getRate())
                        .build())
                .collect(Collectors.toList());

        String safeName = dto.getProfileName()
                .toLowerCase()
                .replaceAll("\\s+", "_") // Replace spaces with underscores
                .replaceAll("[^a-z0-9_]", ""); // Remove all non-alphanumeric characters except underscore

        String taxProfileId = "tax_" + safeName;
        // String taxProfileId = "tax_" + dto.getProfileName().toLowerCase().replaceAll("\\s+", "_");
        TaxProfile taxProfile = TaxProfile.builder()
                .taxProfileId(taxProfileId)
                .profileName(dto.getProfileName())

                .totalRate(dto.getTotalRate())
                .components(componentModels)
                .build();
        return taxProfileRepository.save(orgId, taxProfile);
    }

    /*public Medicine updateMedicine(String orgId, String branchId, String medicineId, UpdateMedicineRequest dto) {
        // 1. First, ensure the medicine exists before updating.
        Medicine existingMedicine = medicineRepository.findById(orgId, branchId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine with ID " + medicineId + " not found."));

        // Check if the name is being changed and if the new name already exists
        String newName = dto.getName();
        if (!existingMedicine.getName().equalsIgnoreCase(newName)) {
            boolean medicineWithSameNameExists = medicineRepository.findAllByBranchId(orgId, branchId).stream()
                    .filter(m -> !m.getMedicineId().equals(medicineId)) // Exclude the current medicine
                    .anyMatch(m -> m.getName().equalsIgnoreCase(newName));

            if (medicineWithSameNameExists) {
                throw new IllegalArgumentException("A medicine with the name '" + newName + "' already exists in this branch.");
            }
        }
        
        // 2. Update the fields from the request DTO.
        existingMedicine.setName(dto.getName());

        existingMedicine.setCategory(dto.getCategory());
        existingMedicine.setLocation(dto.getLocation());
        existingMedicine.setUnitOfMeasurement(dto.getUnitOfMeasurement());
        existingMedicine.setLowStockThreshold(dto.getLowStockThreshold());
        existingMedicine.setTaxProfileId(dto.getTaxProfileId());
        existingMedicine.setUnitPrice(dto.getUnitPrice());

        // 3. Save the updated object, overwriting the old one.
        return medicineRepository.save(orgId, branchId, existingMedicine);
    }*/

    public Medicine updateMedicine(String orgId, String branchId, String medicineId, UpdateMedicineRequest dto) {
        // 1. Fetch the existing medicine to ensure it exists.
        Medicine existingMedicine = medicineRepository.findById(orgId, branchId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine with ID " + medicineId + " not found."));

        // 2. Efficiently check if the new name is already taken by another medicine.
        String newName = dto.getName();
        if (!existingMedicine.getName().equalsIgnoreCase(newName)) {
            medicineRepository.findByNameIgnoreCaseExcludingId(orgId, branchId, newName, medicineId)
                    .ifPresent(m -> {
                        throw new IllegalArgumentException("A medicine with the name '" + newName + "' already exists.");
                    });
        }

        // 3. --- THIS IS THE CORRECTED MAPPING ---
        // Update all permissible fields from the request DTO.
        existingMedicine.setName(dto.getName());
        //existingMedicine.setNormalizedName(dto.getName().toLowerCase()); // Update the normalized field
        existingMedicine.setGenericName(dto.getGenericName());
        existingMedicine.setCategory(dto.getCategory());
        existingMedicine.setManufacturer(dto.getManufacturer());
        existingMedicine.setLocation(dto.getLocation());
        existingMedicine.setUnitOfMeasurement(dto.getUnitOfMeasurement());
        existingMedicine.setLowStockThreshold(dto.getLowStockThreshold());
        existingMedicine.setTaxProfileId(dto.getTaxProfileId());
        existingMedicine.setUnitPrice(dto.getUnitPrice());
        existingMedicine.setSku(dto.getSku());
        existingMedicine.setHsnCode(dto.getHsnCode());
        existingMedicine.setStatus(dto.getStatus());
        // Note: 'status' is not updated here, as that's handled by a separate delete/deactivate method.

        // 4. Save the updated object, overwriting the old one.
        return medicineRepository.save(orgId, branchId, existingMedicine);
    }
    public void deleteMedicineSoft(String orgId, String branchId, String medicineId) {
        // SOFT DELETE implementation
        Medicine medicine = medicineRepository.findById(orgId, branchId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine with ID " + medicineId + " not found."));

        // TODO: Add business logic check. Can you delete a medicine with stock > 0?
        // if (medicine.getQuantityInStock() > 0) {
        //     throw new IllegalStateException("Cannot delete a medicine that is still in stock.");
        // }

        medicine.setStatus("INACTIVE"); // Change status instead of deleting.
        medicineRepository.save(orgId, branchId, medicine);
    }

    public void deleteSupplierSoft(String orgId, String supplierId) {
        // SOFT DELETE implementation
        Supplier supplier = supplierRepository.findById(orgId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier with ID " + supplierId + " not found."));

        supplier.setStatus("INACTIVE");
        supplierRepository.save(orgId, supplier);
    }

    /**
     * Performs a "hard delete" on a Supplier, permanently removing them from the database.
     * It first checks if the supplier exists before attempting deletion.
     *
     * @param orgId The organization ID from the security context.
     * @param supplierId The ID of the supplier to delete.
     * @throws ResourceNotFoundException if the supplier does not exist.
     */
    public void deleteSupplier(String orgId, String supplierId) {
        // 1. Fetch the supplier using the existing findById method.
        //    This serves two purposes:
        //    a) It confirms the supplier exists. If not, it throws ResourceNotFoundException.
        //    b) It gives us the full Supplier object, which we can use for business logic checks.
        Supplier supplierToDelete = supplierRepository.findById(orgId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier with ID " + supplierId + " not found."));

        // 2. --- CRITICAL BUSINESS LOGIC CHECK ---
        //    Now that we have the supplier object, we can check its balance.
        //    You should prevent deletion if the supplier has an outstanding balance.
        Double balance = supplierToDelete.getOutstandingBalance();
        if (balance != null && balance != 0.0) {
            throw new IllegalStateException("Cannot delete supplier '" + supplierToDelete.getName() + "' as they have an outstanding balance of " + balance);
        }

        // You could also add another repository call here to check if the supplier has
        // been used in any purchase invoices, which is another common reason to prevent deletion.

        // 3. If all checks pass, call the repository to permanently delete the document.
        supplierRepository.deleteById(orgId, supplierId);
    }

    public Supplier updateSupplier(String orgId, String supplierId, UpdateSupplierRequest dto) {
        Supplier existingSupplier = supplierRepository.findById(orgId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier with ID " + supplierId + " not found."));

        // Check if the name is being changed and if the new name already exists
        String newName = dto.getName();
        if (!existingSupplier.getName().equals(newName)) {
            boolean supplierWithSameNameExists = supplierRepository.findAllByOrganizationId(orgId).stream()
                    .filter(s -> !s.getSupplierId().equals(supplierId)) // Exclude the current supplier
                    .anyMatch(s -> s.getName().equalsIgnoreCase(newName));

            if (supplierWithSameNameExists) {
                throw new IllegalArgumentException("A supplier with the name '" + newName + "' already exists.");
            }
        }

        existingSupplier.setName(dto.getName());
        existingSupplier.setAddress(dto.getAddress());
        existingSupplier.setStatus(dto.getStatus());
        existingSupplier.setEmail(dto.getEmail());
        existingSupplier.setGstin(dto.getGstin());
        existingSupplier.setMobileNumber(dto.getMobileNumber());
        existingSupplier.setDrugLicenseNumber(dto.getDrugLicenseNumber());
        existingSupplier.setContactPerson(dto.getContactPerson());
        return supplierRepository.save(orgId, existingSupplier);
    }

    /**
     * Retrieves a list of all medicines for a branch and enriches each one
     * with its real-time calculated stock level.
     *
     * @param orgId The organization ID of the authenticated user.
     * @param branchId The branch ID of the authenticated user.
     * @return A list of DTOs suitable for the Stock Details UI.
     */
    public List<MedicineStockResponse> getAllMedicinesForBranch(String orgId, String branchId) {
        // 1. Fetch all the medicine master documents for the branch.
        List<Medicine> medicines = medicineRepository.findAllByBranchId(orgId, branchId);

        // 2. Use a Java Stream to process each medicine.
        return medicines.stream()
                .map(medicine -> {
                    // 3. For each medicine, fetch all of its batch documents.
                    //    This uses the non-transactional read method we created earlier.
                    List<MedicineBatch> batches = medicineBatchRepository.findAllBatchesForMedicine(
                            orgId, branchId, medicine.getMedicineId());

                    // 4. Calculate the total stock by summing the quantity from each batch.
                    int totalStock = batches.stream()
                            .mapToInt(MedicineBatch::getQuantityAvailable)
                            .sum();

                    // 5. Build the final response DTO with the calculated stock.
                    return MedicineStockResponse.builder()
                            .medicineId(medicine.getMedicineId())
                            .name(medicine.getName())
                            .genericName(medicine.getGenericName())
                            .category(medicine.getCategory())
                            .manufacturer(medicine.getManufacturer())
                            .location(medicine.getLocation())
                            .quantityInStock(totalStock)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public TaxProfile updateTaxProfile(String orgId, String taxProfileId, UpdateTaxProfileRequest dto) {
        // 1. Fetch the existing profile to ensure it exists.
        TaxProfile existingProfile = taxProfileRepository.findById(orgId, taxProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Tax Profile with ID " + taxProfileId + " not found."));

        // 2. Check if the name is being changed and if the new name is already taken.
        String newName = dto.getProfileName();
        if (!existingProfile.getProfileName().equalsIgnoreCase(newName)) {
            taxProfileRepository.findByProfileNameIgnoreCaseExcludingId(orgId, newName, taxProfileId)
                    .ifPresent(p -> {
                        throw new IllegalArgumentException("A tax profile with the name '" + newName + "' already exists.");
                    });
        }

        // 3. Map the DTO to the domain model for the components.
        List<TaxComponent> componentModels = dto.getComponents().stream()
                .map(componentDto -> TaxComponent.builder()
                        .name(componentDto.getComponentName())
                        .rate(componentDto.getRate())
                        .build())
                .collect(Collectors.toList());

        // 4. Update the fields on the existing profile object.
        existingProfile.setProfileName(dto.getProfileName());
       // existingProfile.setNormalizedProfileName(dto.getProfileName().toLowerCase().trim()); // Update normalized field
        existingProfile.setTotalRate(dto.getTotalRate());
        existingProfile.setComponents(componentModels);

        // 5. Save the updated object.
        return taxProfileRepository.save(orgId, existingProfile);
    }

    /**
     * Performs a "soft delete" on a Tax Profile by changing its status to INACTIVE.
     * It first checks if the profile is currently in use by any active medicines.
     *
     * @param orgId The organization ID from the security context.
     * @param taxProfileId The ID of the tax profile to deactivate.
     * @throws IllegalStateException if the tax profile is still in use.
     * @throws ResourceNotFoundException if the tax profile does not exist.
     */
    public void deleteTaxProfileSoft(String orgId, String taxProfileId) {
        // 1. Fetch the existing profile to ensure it exists.
        TaxProfile taxProfile = taxProfileRepository.findById(orgId, taxProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Tax Profile with ID " + taxProfileId + " not found."));

        // 2. --- CRITICAL BUSINESS LOGIC CHECK ---
        //    Check if any active medicine is currently using this tax profile.
        boolean isInUse = medicineRepository.existsByTaxProfileId(orgId, taxProfileId);
        if (isInUse) {
            throw new IllegalStateException("Cannot delete tax profile '" + taxProfile.getProfileName() + "' as it is currently assigned to one or more medicines.");
        }

        // 3. If it's not in use, perform the soft delete by changing the status.
        taxProfile.setStatus("INACTIVE");

        // 4. Save the updated object.
        taxProfileRepository.save(orgId, taxProfile);
    }

    /**
     * Performs a "hard delete" on a Tax Profile, permanently removing it.
     * <p>
     * It includes a critical safety check to prevent deletion if the profile
     * is currently assigned to any active medicines.
     *
     * @param orgId The organization ID from the security context.
     * @param taxProfileId The ID of the tax profile to delete.
     * @throws IllegalStateException if the tax profile is still in use.
     * @throws ResourceNotFoundException if the tax profile does not exist.
     */
    public void deleteTaxProfile(String orgId, String taxProfileId) {
        // 1. First, check if the tax profile even exists.
        //    This provides a clear error if the client tries to delete an invalid ID.
        TaxProfile taxProfile = taxProfileRepository.findById(orgId, taxProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Tax Profile with ID " + taxProfileId + " not found."));

        // 2. --- CRITICAL BUSINESS LOGIC CHECK ---
        //    Check if any active medicine is currently using this tax profile.
        //    This prevents breaking data integrity for your products.
        boolean isInUse = medicineRepository.existsByTaxProfileId(orgId, taxProfileId);
        if (isInUse) {
            throw new IllegalStateException("Cannot delete tax profile '" + taxProfile.getProfileName() + "' as it is currently assigned to one or more medicines.");
        }

        // 3. If all checks pass, call the repository to permanently delete the document.
        taxProfileRepository.deleteById(orgId, taxProfileId);
    }

    /**
     * Cleans up all tax profiles except the "no tax" profile.
     * This method is useful for resolving tax profile inconsistencies.
     * 
     * @param orgId The organization ID from the security context.
     * @return CleanupResult containing the number of profiles deleted and any errors
     */
    public CleanupResult cleanupTaxProfiles(String orgId) {
        List<TaxProfile> allProfiles = taxProfileRepository.findAllByOrganizationId(orgId);
        int deletedCount = 0;
        int errorCount = 0;
        StringBuilder errors = new StringBuilder();
        
        for (TaxProfile profile : allProfiles) {
            // Skip the "no tax" profile - keep profiles that contain "no" and "tax" in name
            String profileName = profile.getProfileName().toLowerCase();
            String profileId = profile.getTaxProfileId().toLowerCase();
            
            if (profileName.contains("no") && profileName.contains("tax") || 
                profileId.contains("no") && profileId.contains("tax") ||
                profileId.equals("tax_no_tax")) {
                continue; // Skip deletion of "no tax" profiles
            }
            
            try {
                // Check if the profile is in use before deleting
                boolean isInUse = medicineRepository.existsByTaxProfileId(orgId, profile.getTaxProfileId());
                if (isInUse) {
                    // Update medicines using this profile to use "no tax" profile
                    List<Medicine> medicinesUsingProfile = medicineRepository.findAllByBranchId(orgId, "")
                        .stream()
                        .filter(m -> profile.getTaxProfileId().equals(m.getTaxProfileId()))
                        .collect(Collectors.toList());
                    
                    // Find or create a "no tax" profile
                    String noTaxProfileId = findOrCreateNoTaxProfile(orgId);
                    
                    // Update medicines to use no tax profile
                    for (Medicine medicine : medicinesUsingProfile) {
                        medicine.setTaxProfileId(noTaxProfileId);
                        medicineRepository.save(orgId, "test-org-123", medicine);
                    }
                }
                
                // Now delete the profile
                taxProfileRepository.deleteById(orgId, profile.getTaxProfileId());
                deletedCount++;
                
            } catch (Exception e) {
                errorCount++;
                errors.append("Failed to delete profile ").append(profile.getProfileName())
                      .append(": ").append(e.getMessage()).append("; ");
            }
        }
        
        return new CleanupResult(deletedCount, errorCount, errors.toString());
    }
    
    /**
     * Finds an existing "no tax" profile or creates one if it doesn't exist.
     * 
     * @param orgId The organization ID
     * @return The ID of the "no tax" profile
     */
    private String findOrCreateNoTaxProfile(String orgId) {
        List<TaxProfile> allProfiles = taxProfileRepository.findAllByOrganizationId(orgId);
        
        // Look for existing "no tax" profile
        for (TaxProfile profile : allProfiles) {
            String profileName = profile.getProfileName().toLowerCase();
            String profileId = profile.getTaxProfileId().toLowerCase();
            
            if ((profileName.contains("no") && profileName.contains("tax")) || 
                profileId.contains("no_tax") || profileId.equals("tax_no_tax")) {
                return profile.getTaxProfileId();
            }
        }
        
        // Create a new "no tax" profile if none exists
        TaxProfile noTaxProfile = TaxProfile.builder()
                .taxProfileId("tax_no_tax")
                .profileName("No Tax")
                .totalRate(0.0)
                .components(List.of()) // Empty list for no tax components
                .build();
                
        TaxProfile savedProfile = taxProfileRepository.save(orgId, noTaxProfile);
        return savedProfile.getTaxProfileId();
    }
    
    /**
     * Result class for cleanup operations
     */
    public static class CleanupResult {
        private final int deletedCount;
        private final int errorCount;
        private final String errors;
        
        public CleanupResult(int deletedCount, int errorCount, String errors) {
            this.deletedCount = deletedCount;
            this.errorCount = errorCount;
            this.errors = errors;
        }
        
        public int getDeletedCount() { return deletedCount; }
        public int getErrorCount() { return errorCount; }
        public String getErrors() { return errors; }
    }

    /**
     * Performs a "hard delete" on a Medicine, permanently removing it and all its batches.
     * <p>
     * Includes a critical safety check to prevent the deletion of a medicine
     * that has any remaining stock.
     *
     * @param orgId The organization ID from the security context.
     * @param branchId The branch ID from the security context.
     * @param medicineId The ID of the medicine to delete.
     * @throws IllegalStateException if the medicine still has stock.
     * @throws ResourceNotFoundException if the medicine does not exist.
     */
    public void deleteMedicine(String orgId, String branchId, String medicineId) {
        // 1. Check if the medicine exists before proceeding.
        medicineRepository.findById(orgId, branchId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine with ID " + medicineId + " not found."));


        // 2. --- CRITICAL BUSINESS LOGIC CHECK ---
        //    Fetch all batches to check the total stock.
        List<MedicineBatch> batches = medicineBatchRepository.findAllBatchesForMedicine(orgId, branchId, medicineId);
        int totalStock = batches.stream().mapToInt(MedicineBatch::getQuantityAvailable).sum();

        if (totalStock > 0) {
            throw new IllegalStateException("Cannot delete medicine. It still has " + totalStock + " units in stock.");
        }

        // TODO: Long-term, also check for sales/purchase history before allowing deletion.

        // 3. If all checks pass, call the repository to permanently delete the document and its sub-collection.
        medicineRepository.deleteByIdHard(orgId, branchId, medicineId);
    }

    /**
     * Fetches the detailed stock view for a single medicine, including
     * a list of all its available batches.
     */
    public MedicineStockDetailResponse getMedicineStockDetails(String orgId, String branchId, String medicineId) {
        // 1. Fetch the master medicine document.
        Medicine medicine = medicineRepository.findById(orgId, branchId, medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine with ID " + medicineId + " not found."));

        // 2. Fetch all batch documents for this medicine.
        List<MedicineBatch> batches = medicineBatchRepository.findAllBatchesForMedicine(orgId, branchId, medicineId);

        // 3. Convert the list of batch models to a list of batch DTOs.
        List<MedicineStockDetailResponse.BatchDetailDto> batchDtos = batches.stream()
                .map(batch -> MedicineStockDetailResponse.BatchDetailDto.builder()
                        .batchId(batch.getBatchId())
                        .batchNo(batch.getBatchNo())
                        .quantityAvailable(batch.getQuantityAvailable())
                        .mrp(batch.getMrp())
                        .expiryDate(batch.getExpiryDate().toDate())
                        .build())
                .collect(Collectors.toList());

        // 4. Calculate the total stock from the batches.
        int totalStock = batchDtos.stream().mapToInt(MedicineStockDetailResponse.BatchDetailDto::getQuantityAvailable).sum();

        // 5. Build the final, rich response DTO.
        return MedicineStockDetailResponse.builder()
                .medicineId(medicine.getMedicineId())
                .name(medicine.getName())
                .genericName(medicine.getGenericName())
                .manufacturer(medicine.getManufacturer())
                .totalStock(totalStock)
                .batches(batchDtos)
                .build();
    }
}
