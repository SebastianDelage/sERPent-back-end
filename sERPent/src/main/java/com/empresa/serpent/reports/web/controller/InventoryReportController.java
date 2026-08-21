package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.reports.service.InventoryReportService;
import com.empresa.serpent.reports.web.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /** One row per branch: consolidated by construction. */
    @PreAuthorize("hasRole('ADMIN')")
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

    /** One row per branch: consolidated by construction. */
    @PreAuthorize("hasRole('ADMIN')")
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

    /** One row per branch: consolidated by construction. */
    @PreAuthorize("hasRole('ADMIN')")
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

    /** ADMIN because this report has NO warehouse parameter to scope it by, not because the
     * data is sensitive. Give it a branch filter and it can be opened to employees. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/replenishment")
    public List<InventoryReplenishmentResponse> getReplenishmentReport() {
        return inventoryReportService.getReplenishmentReport();
    }
}