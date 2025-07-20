package com.cosmicdoc.inventoryservice.config;

import com.cosmicdoc.common.repository.*;
import com.cosmicdoc.common.repository.impl.*;
import com.google.cloud.firestore.Firestore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EntityConfiguration {

    @Bean
    MedicineRepository medicineRepository (Firestore firestore) {
        return new MedicineRepositoryImpl(firestore);
    }
    @Bean
    PurchaseRepository purchaseRepository (Firestore firestore) {
        return new PurchaseRepositoryImpl(firestore);
    }

    @Bean
    PurchaseReturnRepository purchaseReturnRepository (Firestore firestore) {
        return new PurchaseReturnRepositoryImpl(firestore);
    }
    @Bean
    SaleRepository saleRepository (Firestore firestore) {
        return new SaleRepositoryImpl(firestore);
    }
    @Bean
    SalesReturnRepository salesReturnRepository (Firestore firestore) {
        return new SalesReturnRepositoryImpl(firestore);
    }
    @Bean
    SupplierRepository supplierRepository (Firestore firestore) {
        return new SupplierRepositoryImpl(firestore);
    }

    @Bean
    TaxProfileRepository taxProfileRepository (Firestore firestore) {
        return new TaxProfileRepositoryImpl( firestore);
    }

    @Bean
    MedicineBatchRepository medicineBatchRepository (Firestore firestore) {
        return new MedicineBatchRepositoryImpl(firestore);
    }
    @Bean
    SupplierPaymentRepository supplierPaymentRepository (Firestore firestore) {
        return new SupplierPaymentRepositoryImpl(firestore);
    }

}
