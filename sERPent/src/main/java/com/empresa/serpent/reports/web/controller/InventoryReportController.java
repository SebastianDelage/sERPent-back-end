package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.reports.service.InventoryReportService;
import com.empresa.serpent.reports.web.dto.response.InventoryByWarehouseResponse;
import com.empresa.serpent.reports.web.dto.response.InventorySummaryResponse;
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
}