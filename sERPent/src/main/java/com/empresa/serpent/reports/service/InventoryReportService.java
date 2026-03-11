package com.empresa.serpent.reports.service;

import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.reports.web.dto.response.InventoryByWarehouseResponse;
import com.empresa.serpent.reports.web.dto.response.InventorySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}