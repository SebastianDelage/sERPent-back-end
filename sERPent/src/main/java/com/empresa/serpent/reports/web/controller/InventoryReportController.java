package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.reports.service.InventoryReportService;
import com.empresa.serpent.reports.web.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports/inventory")
@RequiredArgsConstructor
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    @GetMapping("/summary")
    public List<InventorySummaryResponse> getInventorySummary(
            @RequestParam(required = false) Long warehouseId
    ) {
        return inventoryReportService.getInventorySummary(warehouseId);
    }

    @GetMapping("/by-warehouse")
    public List<InventoryByWarehouseResponse> getInventoryByWarehouse() {
        return inventoryReportService.getInventoryByWarehouse();
    }

    @GetMapping("/low-stock")
    public List<LowStockResponse> getLowStockReport(
            @RequestParam(required = false) Long warehouseId
    ) {
        return inventoryReportService.getLowStockReport(warehouseId);
    }

    @GetMapping("/warehouse-summary")
    public List<WarehouseSummaryResponse> getWarehouseSummary() {
        return inventoryReportService.getWarehouseSummary();
    }

    @GetMapping("/movements/by-type")
    public List<InventoryMovementsByTypeResponse> getInventoryMovementsByType(
            @RequestParam(required = false) Long warehouseId
    ) {
        return inventoryReportService.getInventoryMovementsByType(warehouseId);
    }

    @GetMapping("/movements/by-warehouse")
    public List<InventoryMovementsByWarehouseResponse> getInventoryMovementsByWarehouse() {
        return inventoryReportService.getInventoryMovementsByWarehouse();
    }

    @GetMapping("/movements/by-product")
    public List<InventoryMovementsByProductResponse> getInventoryMovementsByProduct(
            @RequestParam(required = false) Long warehouseId
    ) {
        return inventoryReportService.getInventoryMovementsByProduct(warehouseId);
    }

    @GetMapping("/replenishment")
    public List<InventoryReplenishmentResponse> getReplenishmentReport() {
        return inventoryReportService.getReplenishmentReport();
    }
}