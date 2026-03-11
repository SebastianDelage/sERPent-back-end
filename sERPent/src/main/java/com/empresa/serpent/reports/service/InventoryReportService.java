package com.empresa.serpent.reports.service;

import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.reports.web.dto.response.InventoryByWarehouseResponse;
import com.empresa.serpent.reports.web.dto.response.InventorySummaryResponse;
import com.empresa.serpent.reports.web.dto.response.WarehouseSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private final StockQueryService stockQueryService;

    public List<InventorySummaryResponse> getInventorySummary() {
        return stockQueryService.getTotalStockGroupedByProduct(false)
                .stream()
                .map(this::toInventorySummaryResponse)
                .toList();
    }

    public List<InventoryByWarehouseResponse> getInventoryByWarehouse() {
        return stockQueryService.getStock(new StockFilter(null, null, null))
                .stream()
                .map(this::toInventoryByWarehouseResponse)
                .toList();
    }

    public List<LowStockResponse> getLowStockReport() {
        return stockQueryService.getLowStock();
    }

    public List<WarehouseSummaryResponse> getWarehouseSummary() {

        List<StockResponse> stockRows = stockQueryService.getStock(new StockFilter(null, null, null));

        Map<WarehouseKey, List<StockResponse>> grouped = stockRows.stream()
                .collect(Collectors.groupingBy(row -> new WarehouseKey(
                        row.warehouseId(),
                        row.warehouseName()
                )));

        return grouped.entrySet()
                .stream()
                .map(entry -> {
                    WarehouseKey key = entry.getKey();
                    List<StockResponse> rows = entry.getValue();

                    long distinctProducts = rows.stream()
                            .map(StockResponse::productId)
                            .distinct()
                            .count();

                    BigDecimal totalUnits = rows.stream()
                            .map(StockResponse::stock)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new WarehouseSummaryResponse(
                            key.warehouseId(),
                            key.warehouseName(),
                            distinctProducts,
                            totalUnits
                    );
                })
                .sorted((a, b) -> a.warehouseName().compareToIgnoreCase(b.warehouseName()))
                .toList();
    }

    private InventorySummaryResponse toInventorySummaryResponse(ProductStockResponse row) {
        return new InventorySummaryResponse(
                row.productId(),
                row.productName(),
                row.totalStock()
        );
    }

    private InventoryByWarehouseResponse toInventoryByWarehouseResponse(StockResponse row) {
        return new InventoryByWarehouseResponse(
                row.productId(),
                row.productName(),
                row.warehouseId(),
                row.warehouseName(),
                row.stock()
        );
    }

    private record WarehouseKey(
            Long warehouseId,
            String warehouseName
    ) {
    }
}