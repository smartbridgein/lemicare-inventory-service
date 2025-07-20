package com.cosmicdoc.inventoryservice.service;

import com.cosmicdoc.common.model.Medicine;
import com.cosmicdoc.common.model.MedicineBatch;
import com.cosmicdoc.common.model.Sale;
import com.cosmicdoc.common.repository.MedicineBatchRepository;
import com.cosmicdoc.common.repository.MedicineRepository;
import com.cosmicdoc.common.repository.SaleRepository;
import com.cosmicdoc.inventoryservice.dto.response.DailySalesSummaryResponse;
import com.cosmicdoc.inventoryservice.dto.response.StockByCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final SaleRepository saleRepository;

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
}
