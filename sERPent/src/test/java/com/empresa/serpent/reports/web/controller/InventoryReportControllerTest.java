package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.reports.service.InventoryReportService;
import com.empresa.serpent.reports.web.dto.response.InventoryByWarehouseResponse;
import com.empresa.serpent.reports.web.dto.response.InventorySummaryResponse;
import com.empresa.serpent.reports.web.dto.response.WarehouseSummaryResponse;
import com.empresa.serpent.shared.security.JwtAuthenticationFilter;
import com.empresa.serpent.shared.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = InventoryReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = { JwtAuthenticationFilter.class, JwtService.class }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class InventoryReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryReportService inventoryReportService;

    @Test
    @DisplayName("Should return inventory summary report")
    void shouldReturnInventorySummaryReport() throws Exception {

        List<InventorySummaryResponse> response = List.of(
                new InventorySummaryResponse(1L, "Pollo entero", new BigDecimal("27.000")),
                new InventorySummaryResponse(2L, "Pata muslo", new BigDecimal("19.000"))
        );

        given(inventoryReportService.getInventorySummary(null)).willReturn(response);

        mockMvc.perform(get("/api/reports/inventory/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].productName").value("Pollo entero"))
                .andExpect(jsonPath("$[0].totalStock").value(27.000))
                .andExpect(jsonPath("$[1].productId").value(2))
                .andExpect(jsonPath("$[1].productName").value("Pata muslo"))
                .andExpect(jsonPath("$[1].totalStock").value(19.000));
    }

    @Test
    @DisplayName("Should return inventory by warehouse report")
    void shouldReturnInventoryByWarehouseReport() throws Exception {

        List<InventoryByWarehouseResponse> response = List.of(
                new InventoryByWarehouseResponse(1L, "Pollo entero", 1L, "Depósito Central", new BigDecimal("19.000")),
                new InventoryByWarehouseResponse(1L, "Pollo entero", 2L, "Sucursal Norte", new BigDecimal("8.000"))
        );

        given(inventoryReportService.getInventoryByWarehouse()).willReturn(response);

        mockMvc.perform(get("/api/reports/inventory/by-warehouse"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].productName").value("Pollo entero"))
                .andExpect(jsonPath("$[0].warehouseId").value(1))
                .andExpect(jsonPath("$[0].warehouseName").value("Depósito Central"))
                .andExpect(jsonPath("$[0].stock").value(19.000))
                .andExpect(jsonPath("$[1].warehouseId").value(2))
                .andExpect(jsonPath("$[1].warehouseName").value("Sucursal Norte"))
                .andExpect(jsonPath("$[1].stock").value(8.000));
    }

    @Test
    @DisplayName("Should return low stock report")
    void shouldReturnLowStockReport() throws Exception {

        List<LowStockResponse> response = List.of(
                new LowStockResponse(2L, "Pata muslo", 1L, "Depósito Central",
                        new BigDecimal("19.000"), new BigDecimal("20.000"), false, new BigDecimal("1.000"))
        );

        given(inventoryReportService.getLowStockReport(null)).willReturn(response);

        mockMvc.perform(get("/api/reports/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].productId").value(2))
                .andExpect(jsonPath("$[0].productName").value("Pata muslo"))
                .andExpect(jsonPath("$[0].currentStock").value(19.000))
                .andExpect(jsonPath("$[0].minimumStock").value(20.000));
    }

    @Test
    @DisplayName("Should return warehouse summary report")
    void shouldReturnWarehouseSummaryReport() throws Exception {

        List<WarehouseSummaryResponse> response = List.of(
                new WarehouseSummaryResponse(1L, "Depósito Central", 3L, new BigDecimal("53.000")),
                new WarehouseSummaryResponse(2L, "Sucursal Norte", 2L, new BigDecimal("13.000"))
        );

        given(inventoryReportService.getWarehouseSummary()).willReturn(response);

        mockMvc.perform(get("/api/reports/inventory/warehouse-summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].warehouseId").value(1))
                .andExpect(jsonPath("$[0].warehouseName").value("Depósito Central"))
                .andExpect(jsonPath("$[0].distinctProducts").value(3))
                .andExpect(jsonPath("$[0].totalUnits").value(53.000))
                .andExpect(jsonPath("$[1].warehouseId").value(2))
                .andExpect(jsonPath("$[1].warehouseName").value("Sucursal Norte"))
                .andExpect(jsonPath("$[1].distinctProducts").value(2))
                .andExpect(jsonPath("$[1].totalUnits").value(13.000));
    }
}