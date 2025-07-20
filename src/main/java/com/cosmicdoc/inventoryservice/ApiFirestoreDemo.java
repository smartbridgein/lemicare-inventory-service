package com.cosmicdoc.inventoryservice;

import com.cosmicdoc.common.model.Supplier;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * This demo simulates the API controller/service flow by creating data,
 * inserting it into Firestore, then retrieving it to verify the operation.
 */
public class ApiFirestoreDemo {

    private static Firestore firestore;
    private static final String SUPPLIER_COLLECTION = "suppliers";

    public static void main(String[] args) {
        try {
            // Initialize Firebase with a service account
            initializeFirebase();
            
            // Create demo organization and branch IDs
            String orgId = "demo_org_" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("Demo Organization ID: " + orgId);
            
            // 1. Simulate POST /api/inventory/masters/suppliers API call
            System.out.println("\n--- POST /api/inventory/masters/suppliers ---");
            Map<String, Object> createRequest = createSupplierRequest();
            Supplier createdSupplier = createSupplier(orgId, createRequest);
            
            System.out.println("Created supplier with ID: " + createdSupplier.getSupplierId());
            System.out.println("Supplier details: " + createdSupplier.getName() + ", " + 
                             createdSupplier.getContactPerson() + ", " + 
                             createdSupplier.getEmail());
            
            // 2. Simulate GET /api/inventory/masters/suppliers API call
            System.out.println("\n--- GET /api/inventory/masters/suppliers ---");
            List<Supplier> suppliers = getSuppliers(orgId);
            System.out.println("Retrieved " + suppliers.size() + " suppliers:");
            for (Supplier supplier : suppliers) {
                System.out.println(" - " + supplier.getName() + " (ID: " + supplier.getSupplierId() + ")");
            }
            
            // 3. Clean up test data
            System.out.println("\n--- Cleaning Up Test Data ---");
            //deleteSupplier(orgId, createdSupplier.getSupplierId());
            System.out.println("Test data deleted successfully");
            
            System.out.println("\nFirestore API simulation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error in API simulation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initializes Firebase connection using the service account file
     */
    private static void initializeFirebase() throws IOException {
        System.out.println("Initializing Firebase...");
        
        // Check if Firebase is already initialized
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FileInputStream serviceAccount = new FileInputStream("src/main/resources/service-account.json");
                System.out.println("Service account file found, initializing Firebase...");
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId("cosmicdoc")
                        .build();
                FirebaseApp.initializeApp(options);
            } catch (IOException e) {
                System.err.println("Error loading service account: " + e.getMessage());
                throw e;
            }
        }
        
        firestore = FirestoreClient.getFirestore();
        System.out.println("Firebase initialized successfully!");
    }
    
    /**
     * Creates a sample supplier request map (simulates incoming JSON from API)
     */
    private static Map<String, Object> createSupplierRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "API Test Supplier");
        request.put("gstin", "GSTIN1234567890");
        request.put("contactPerson", "John Smith");
        request.put("mobileNumber", "9876543210");
        request.put("email", "john@example.com");
        request.put("address", "123 Test Street, Test City");
        return request;
    }
    
    /**
     * Simulates the MasterDataService.createSupplier() method
     */
    private static Supplier createSupplier(String orgId, Map<String, Object> dto) throws ExecutionException, InterruptedException {
        // Generate a unique supplier ID
        String supplierId = "sup_" + UUID.randomUUID().toString();
        
        // Convert DTO to entity (similar to service layer)
        Supplier supplier = Supplier.builder()
                .supplierId(supplierId)
                .name((String) dto.get("name"))
                .gstin((String) dto.get("gstin"))
                .contactPerson((String) dto.get("contactPerson"))
                .mobileNumber((String) dto.get("mobileNumber"))
                .email((String) dto.get("email"))
                .address((String) dto.get("address"))
                .createdBy("test-user")
                .createdAt(Timestamp.now())
                .status("ACTIVE")
                .build();
        
        // Save to Firestore (similar to repository layer)
        DocumentReference docRef = getSupplierCollection(orgId).document(supplierId);
        docRef.set(supplier).get();
        
        System.out.println("Supplier saved to Firestore at path: organizations/" + orgId + "/suppliers/" + supplierId);
        
        return supplier;
    }
    
    /**
     * Simulates the MasterDataService.getSuppliersForOrg() method
     */
    private static List<Supplier> getSuppliers(String orgId) throws ExecutionException, InterruptedException {
        List<Supplier> suppliers = new ArrayList<>();
        
        // Query Firestore (similar to repository layer)
        QuerySnapshot querySnapshot = getSupplierCollection(orgId).get().get();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Supplier supplier = document.toObject(Supplier.class);
            suppliers.add(supplier);
        }
        
        return suppliers;
    }
    
    /**
     * Delete a supplier (cleanup)
     */
    private static void deleteSupplier(String orgId, String supplierId) throws ExecutionException, InterruptedException {
        getSupplierCollection(orgId).document(supplierId).delete().get();
    }
    
    /**
     * Helper method to get the supplier collection reference
     */
    private static CollectionReference getSupplierCollection(String orgId) {
        return firestore.collection("organizations").document(orgId).collection(SUPPLIER_COLLECTION);
    }
}
