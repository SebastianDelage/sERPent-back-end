package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.reports.service.InventoryReportService;
import com.empresa.serpent.reports.web.dto.response.InventoryByWarehouseResponse;
import com.empresa.serpent.reports.web.dto.response.InventoryMovementsByTypeResponse;
import com.empresa.serpent.reports.web.dto.response.InventorySummaryResponse;
import com.empresa.serpent.reports.web.dto.response.WarehouseSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports/inventory")
@RequiredArgsConstructor
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    @GetMapping("/summary")
    public List<InventorySummaryResponse> getInventorySummary() {
        return inventoryReportService.getInventorySummary();
    }

    @GetMapping("/by-warehouse")
    public List<InventoryByWarehouseResponse> getInventoryByWarehouse() {
        return inventoryReportService.getInventoryByWarehouse();
    }

    @GetMapping("/low-stock")
    public List<LowStockResponse> getLowStockReport() {
        return inventoryReportService.getLowStockReport();
    }

    @GetMapping("/warehouse-summary")
    public List<WarehouseSummaryResponse> getWarehouseSummary() {
        return inventoryReportService.getWarehouseSummary();
    }

    @GetMapping("/movements/by-type")
    public List<InventoryMovementsByTypeResponse> getInventoryMovementsByType() {
        return inventoryReportService.getInventoryMovementsByType();
    }
}