package com.cosmicdoc.inventoryservice;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.cloud.Timestamp;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * This is a demonstration of how the inventory-service APIs connect to Firestore
 * for saving and retrieving data. It simulates the repository operations
 * without requiring the full Spring context.
 */
public class FirestoreDemo {

    private static Firestore firestore;

    public static void main(String[] args) {
        try {
            // Initialize Firebase with a service account
            initializeFirebase();
            
            // Example data using structure similar to models in the application
            String organizationId = "demo_org_" + UUID.randomUUID().toString().substring(0, 8);
            String branchId = "demo_branch_" + UUID.randomUUID().toString().substring(0, 8);
            
            System.out.println("Demo Organization ID: " + organizationId);
            System.out.println("Demo Branch ID: " + branchId);
            
            // Create a supplier
            Map<String, Object> supplier = createSupplier(organizationId);
            System.out.println("Created supplier with ID: " + supplier.get("supplierId"));
            
            // Create a medicine in the branch
            Map<String, Object> medicine = createMedicine(organizationId, branchId);
            System.out.println("Created medicine with ID: " + medicine.get("medicineId"));
            
            // Retrieve medicines for the branch
            List<Map<String, Object>> medicines = getMedicinesForBranch(organizationId, branchId);
            System.out.println("Retrieved " + medicines.size() + " medicine(s) for branch");
            
            // Clean up test data
            System.out.println("Cleaning up test data...");
            deleteTestData(organizationId, branchId, (String)supplier.get("supplierId"), (String)medicine.get("medicineId"));
            
            System.out.println("Firestore demo completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error in Firestore demo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeFirebase() throws IOException {
        System.out.println("Initializing Firebase...");
        
        // Check if Firebase is already initialized
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                // Load the service account from resources directory
                FileInputStream serviceAccount = new FileInputStream("src/main/resources/service-account.json");
                System.out.println("Service account file found, initializing Firebase...");
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId("cosmicdoc") // Explicitly set the project ID
                        .build();
                FirebaseApp.initializeApp(options);
            } catch (IOException e) {
                System.err.println("Error loading service account: " + e.getMessage());
                throw e; // We need the service account to proceed
            }
        }
        
        firestore = FirestoreClient.getFirestore();
        System.out.println("Firebase initialized successfully!");
    }

    /**
     * Simulates the createSupplier() operation in MasterDataService
     */
    private static Map<String, Object> createSupplier(String organizationId) throws ExecutionException, InterruptedException {
        // Create a supplier object similar to the Supplier model
        String supplierId = "sup_" + UUID.randomUUID().toString();
        Map<String, Object> supplier = new HashMap<>();
        supplier.put("supplierId", supplierId);
        supplier.put("name", "Demo Supplier");
        supplier.put("gstin", "GSTIN123456789");
        supplier.put("contactPerson", "John Doe");
        supplier.put("mobileNumber", "1234567890");
        supplier.put("email", "supplier@example.com");
        supplier.put("address", "123 Demo Street, Demo City");
        supplier.put("createdAt", Timestamp.now());
        supplier.put("status", "ACTIVE");
        
        // Save to Firestore - this mirrors the SupplierRepositoryImpl.save() method
        DocumentReference docRef = firestore.collection("organizations")
                .document(organizationId)
                .collection("suppliers")
                .document(supplierId);
                
        docRef.set(supplier).get();
        
        System.out.println("Supplier created in Firestore at path: organizations/" + 
                           organizationId + "/suppliers/" + supplierId);
                           
        return supplier;
    }
    
    /**
     * Simulates the createMedicine() operation in MasterDataService
     */
    private static Map<String, Object> createMedicine(String organizationId, String branchId) 
            throws ExecutionException, InterruptedException {
        // Create a medicine object similar to the Medicine model
        String medicineId = "med_" + UUID.randomUUID().toString();
        Map<String, Object> medicine = new HashMap<>();
        medicine.put("medicineId", medicineId);
        medicine.put("name", "Demo Medicine");
        medicine.put("category", "TABLET");
        medicine.put("sku", "DM001");
        medicine.put("hsnCode", "3004");
        medicine.put("unitOfMeasurement", "TAB");
        medicine.put("lowStockThreshold", 10);
        medicine.put("quantityInStock", 100);
        medicine.put("status", "ACTIVE");
        
        // Save to Firestore - this mirrors the MedicineRepositoryImpl.save() method
        DocumentReference docRef = firestore.collection("organizations")
                .document(organizationId)
                .collection("branches")
                .document(branchId)
                .collection("medicines")
                .document(medicineId);
                
        docRef.set(medicine).get();
        
        System.out.println("Medicine created in Firestore at path: organizations/" + 
                           organizationId + "/branches/" + branchId + "/medicines/" + medicineId);
                           
        return medicine;
    }
    
    /**
     * Simulates the getMedicinesForBranch() operation in MasterDataService
     */
    private static List<Map<String, Object>> getMedicinesForBranch(String organizationId, String branchId) 
            throws ExecutionException, InterruptedException {
        // This mirrors the MedicineRepositoryImpl.findAllByBranchId() method
        List<Map<String, Object>> results = new ArrayList<>();
        
        // Query Firestore for all medicines in this branch
        QuerySnapshot querySnapshot = firestore.collection("organizations")
                .document(organizationId)
                .collection("branches")
                .document(branchId)
                .collection("medicines")
                .get().get();
                
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> medicine = document.getData();
            results.add(medicine);
            
            System.out.println("Retrieved medicine: " + medicine.get("name") + 
                              " with stock: " + medicine.get("quantityInStock"));
        }
        
        return results;
    }
    
    /**
     * Cleans up the test data created during the demo
     */
    private static void deleteTestData(String organizationId, String branchId, 
                                     String supplierId, String medicineId) {
        try {
            // Delete the medicine
            firestore.collection("organizations")
                    .document(organizationId)
                    .collection("branches")
                    .document(branchId)
                    .collection("medicines")
                    .document(medicineId)
                    .delete();
                    
            // Delete the supplier
            firestore.collection("organizations")
                    .document(organizationId)
                    .collection("suppliers")
                    .document(supplierId)
                    .delete();
                    
            System.out.println("Test data deleted successfully");
        } catch (Exception e) {
            System.err.println("Error cleaning up test data: " + e.getMessage());
        }
    }
}
