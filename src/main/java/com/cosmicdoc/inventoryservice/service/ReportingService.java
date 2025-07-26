package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.*;
import com.cosmicdoc.common.repository.*;
import com.cosmicdoc.inventoryservice.dto.response.DailySalesSummaryResponse;
import com.cosmicdoc.inventoryservice.dto.response.StockByCategoryResponse;
import com.cosmicdoc.inventoryservice.dto.response.SupplierLedgerResponse;
import com.cosmicdoc.inventoryservice.dto.response.TransactionSummaryDto;
import com.cosmicdoc.inventoryservice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final SaleRepository saleRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;

    /**
     * Generates a stock report grouped by medicine category.
     * WARNING: This can be slow and expensive as it reads ALL medicines and ALL their batches.
     */
    public List<StockByCategoryResponse> getStockByCategory(String orgId, String branchId) {
        // 1. Fetch all medicine master documents for the branch.
        List<Medicine> medicines = medicineRepository.findAllByBranchId(orgId, branchId);

        // 2. Group medicines by category in memory.
        Map<String, List<Medicine>> medicinesByCategory = medicines.stream()
                .filter(m -> m.getCategory() != null)
                .collect(Collectors.groupingBy(Medicine::getCategory));

        // 3. For each category, calculate the total stock.
        List<StockByCategoryResponse> report = new ArrayList<>();
        for (Map.Entry<String, List<Medicine>> entry : medicinesByCategory.entrySet()) {
            String category = entry.getKey();
            List<Medicine> medicinesInCat = entry.getValue();

            int totalStockForCategory = 0;
            for (Medicine med : medicinesInCat) {
                // For each medicine, fetch all its batches and sum their quantity.
                totalStockForCategory += medicineBatchRepository.findAllBatchesForMedicine(orgId, branchId, med.getMedicineId())
                        .stream()
                        .mapToInt(MedicineBatch::getQuantityAvailable)
                        .sum();
            }
            report.add(new StockByCategoryResponse(category, totalStockForCategory));
        }
        return report;
    }

    /**
     * Generates a sales summary for a single day.
     */
    public DailySalesSummaryResponse getDailySalesSummary(String orgId, String branchId, LocalDate date) {
        // This requires a new repository method to query sales by date.
        List<Sale> salesForDay = saleRepository.findAllByBranchIdAndDate(orgId, branchId, date);

        double totalRevenue = salesForDay.stream()
                .mapToDouble(Sale::getGrandTotal)
                .sum();

        return DailySalesSummaryResponse.builder()
                .organizationId(orgId)
                .branchId(branchId)
                .date(date)
                .totalSales(totalRevenue)
                .transactionCount(salesForDay.size())
                .build();
    }

    /**
     * Generates a complete financial ledger for a single supplier.
     * It fetches the supplier's details, balance, and a history of all
     * their purchases and returns.
     */
    public SupplierLedgerResponse getSupplierLedger(String orgId, String branchId, String supplierId) {
        // 1. Fetch the Supplier master data.
        Supplier supplier = supplierRepository.findById(orgId, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier with ID " + supplierId + " not found."));

        // 2. Fetch all Purchase invoices for this supplier in this branch.
        List<Purchase> purchases = purchaseRepository.findAllBySupplierId(orgId, branchId, supplierId);

        // 3. Fetch all Purchase Return invoices for this supplier in this branch.
        List<PurchaseReturn> returns = purchaseReturnRepository.findAllBySupplierId(orgId, branchId, supplierId);

        // 4. Map the purchases to our common transaction DTO.
        List<TransactionSummaryDto> purchaseTransactions = purchases.stream()
                .map(p -> TransactionSummaryDto.builder()
                        .transactionId(p.getPurchaseId())
                        .date(p.getInvoiceDate().toDate())
                        .type("PURCHASE")
                        .referenceId(p.getReferenceId())
                        .invoiceAmount(p.getTotalAmount())
                        .amountPaid(p.getAmountPaid())
                        .build())
                .collect(Collectors.toList());

        // 5. Map the returns to our common transaction DTO.
        List<TransactionSummaryDto> returnTransactions = returns.stream()
                .map(r -> TransactionSummaryDto.builder()
                        .transactionId(r.getPurchaseReturnId())
                        .date(r.getReturnDate().toDate())
                        .type("PURCHASE_RETURN")
                        .referenceId(r.getOriginalPurchaseId()) // Reference the original invoice
                        .amountCredited(r.getTotalReturnedAmount())
                        .build())
                .collect(Collectors.toList());

        // 6. Combine and sort all transactions by date.
        List<TransactionSummaryDto> allTransactions = new ArrayList<>();
        allTransactions.addAll(purchaseTransactions);
        allTransactions.addAll(returnTransactions);
        allTransactions.sort(Comparator.comparing(TransactionSummaryDto::getDate).reversed()); // Newest first

        // TODO: Implement pagination on this sorted list.

        // 7. Build the final, rich response object.
        return SupplierLedgerResponse.builder()
                .supplierId(supplier.getSupplierId())
                .name(supplier.getName())
                .contactPerson(supplier.getContactPerson())
                .email(supplier.getEmail())
                .mobileNumber(supplier.getMobileNumber())
                .outstandingBalance(supplier.getOutstandingBalance())
                .transactions(allTransactions)
                .build();
    }
}
