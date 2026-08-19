package com.empresa.serpent.reports.service;

import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.reports.web.dto.response.InventoryByWarehouseResponse;
import com.empresa.serpent.reports.web.dto.response.InventorySummaryResponse;
import com.empresa.serpent.reports.web.dto.response.WarehouseSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryReportServiceTest {

    @Mock
    private StockQueryService stockQueryService;

    @InjectMocks
    private InventoryReportService inventoryReportService;

    @Test
    @DisplayName("Should return inventory summary mapped from stock query service")
    void shouldReturnInventorySummaryMappedFromStockQueryService() {

        List<ProductStockResponse> stockRows = List.of(
                new ProductStockResponse(1L, "Pollo entero", new BigDecimal("27.000")),
                new ProductStockResponse(2L, "Pata muslo", new BigDecimal("19.000"))
        );

        given(stockQueryService.getTotalStockGroupedByProduct(false, null)).willReturn(stockRows);

        List<InventorySummaryResponse> result = inventoryReportService.getInventorySummary(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).totalStock()).isEqualByComparingTo("27.000");

        assertThat(result.get(1).productId()).isEqualTo(2L);
        assertThat(result.get(1).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(1).totalStock()).isEqualByComparingTo("19.000");

        verify(stockQueryService).getTotalStockGroupedByProduct(false, null);
    }

    @Test
    @DisplayName("Should return inventory by warehouse mapped from stock query service")
    void shouldReturnInventoryByWarehouseMappedFromStockQueryService() {

        List<StockResponse> stockRows = List.of(
                new StockResponse(1L, "Pollo entero", 1L, "Depósito Central", new BigDecimal("19.000")),
                new StockResponse(1L, "Pollo entero", 2L, "Sucursal Norte", new BigDecimal("8.000"))
        );

        given(stockQueryService.getStock(org.mockito.ArgumentMatchers.any())).willReturn(stockRows);

        List<InventoryByWarehouseResponse> result = inventoryReportService.getInventoryByWarehouse();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).warehouseId()).isEqualTo(1L);
        assertThat(result.get(0).warehouseName()).isEqualTo("Depósito Central");
        assertThat(result.get(0).stock()).isEqualByComparingTo("19.000");

        assertThat(result.get(1).warehouseId()).isEqualTo(2L);
        assertThat(result.get(1).warehouseName()).isEqualTo("Sucursal Norte");
        assertThat(result.get(1).stock()).isEqualByComparingTo("8.000");

        verify(stockQueryService).getStock(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Should return low stock report from stock query service")
    void shouldReturnLowStockReportFromStockQueryService() {

        List<LowStockResponse> lowStockRows = List.of(
                new LowStockResponse(2L, "Pata muslo", new BigDecimal("19.000"), new BigDecimal("20.000"))
        );

        given(stockQueryService.getLowStock()).willReturn(lowStockRows);

        List<LowStockResponse> result = inventoryReportService.getLowStockReport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(2L);
        assertThat(result.get(0).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(0).currentStock()).isEqualByComparingTo("19.000");
        assertThat(result.get(0).minimumStock()).isEqualByComparingTo("20.000");

        verify(stockQueryService).getLowStock();
    }

    @Test
    @DisplayName("Should group stock by warehouse and calculate summary")
    void shouldGroupStockByWarehouseAndCalculateSummary() {

        List<StockResponse> stockRows = List.of(
                new StockResponse(3L, "Milanesa de pollo", 1L, "Depósito Central", new BigDecimal("15.000")),
                new StockResponse(2L, "Pata muslo", 1L, "Depósito Central", new BigDecimal("19.000")),
                new StockResponse(1L, "Pollo entero", 1L, "Depósito Central", new BigDecimal("19.000")),
                new StockResponse(3L, "Milanesa de pollo", 2L, "Sucursal Norte", new BigDecimal("5.000")),
                new StockResponse(1L, "Pollo entero", 2L, "Sucursal Norte", new BigDecimal("8.000"))
        );

        given(stockQueryService.getStock(org.mockito.ArgumentMatchers.any())).willReturn(stockRows);

        List<WarehouseSummaryResponse> result = inventoryReportService.getWarehouseSummary();

        assertThat(result).hasSize(2);

        assertThat(result.get(0).warehouseId()).isEqualTo(1L);
        assertThat(result.get(0).warehouseName()).isEqualTo("Depósito Central");
        assertThat(result.get(0).distinctProducts()).isEqualTo(3L);
        assertThat(result.get(0).totalUnits()).isEqualByComparingTo("53.000");

        assertThat(result.get(1).warehouseId()).isEqualTo(2L);
        assertThat(result.get(1).warehouseName()).isEqualTo("Sucursal Norte");
        assertThat(result.get(1).distinctProducts()).isEqualTo(2L);
        assertThat(result.get(1).totalUnits()).isEqualByComparingTo("13.000");

        verify(stockQueryService).getStock(org.mockito.ArgumentMatchers.any());
    }
}