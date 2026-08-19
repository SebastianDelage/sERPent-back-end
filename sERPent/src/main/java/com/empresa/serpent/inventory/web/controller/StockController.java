package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.domain.enums.StockStatusFilter;
import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockPageFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockQueryService stockQueryService;

    @GetMapping
    public List<StockResponse> getStock(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Boolean onlyPositive
    ) {
        StockFilter filter = new StockFilter(productId, warehouseId, onlyPositive);
        return stockQueryService.getStock(filter);
    }

    @GetMapping("/product/{productId}/warehouse/{warehouseId}")
    public BigDecimal getStockByProductAndWarehouse(
            @PathVariable Long productId,
            @PathVariable Long warehouseId
    ) {
        return stockQueryService.getStockByProductAndWarehouse(productId, warehouseId);
    }

    @GetMapping("/product/{productId}")
    public BigDecimal getTotalStockByProduct(@PathVariable Long productId) {
        return stockQueryService.getTotalStockByProduct(productId);
    }

    @GetMapping("/products")
    public List<ProductStockResponse> getTotalStockGroupedByProduct(
            @RequestParam(required = false) Boolean onlyPositive
    ) {
        return stockQueryService.getTotalStockGroupedByProduct(onlyPositive);
    }

    @GetMapping("/warehouse/{warehouseId}")
    public List<StockResponse> getStockByWarehouse(@PathVariable Long warehouseId) {
        return stockQueryService.getStock(new StockFilter(null, warehouseId, null));
    }

    /**
     * The per-warehouse view for the stock screen: filtered and paged in the query.
     * The unpaginated {@code GET /api/stock} above stays as it is for the operational
     * forms, which need a warehouse's whole stock list.
     */
    @GetMapping("/search")
    public Page<StockResponse> searchStock(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StockStatusFilter status,
            Pageable pageable
    ) {
        return stockQueryService.searchStock(
                new StockPageFilter(search, warehouseId, status), pageable);
    }

    /** The per-product view for the stock screen: stock summed across warehouses. */
    @GetMapping("/search/products")
    public Page<ProductStockResponse> searchStockGroupedByProduct(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StockStatusFilter status,
            Pageable pageable
    ) {
        return stockQueryService.searchStockGroupedByProduct(
                new StockPageFilter(search, warehouseId, status), pageable);
    }

    /**
     * How many low-stock situations there are in the filtered set. Ignores the status
     * filter on purpose — see StockQueryService#countLowStockAlerts.
     */
    @GetMapping("/search/low-count")
    public long countLowStockAlerts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long warehouseId
    ) {
        return stockQueryService.countLowStockAlerts(
                new StockPageFilter(search, warehouseId, null));
    }

    /** One row per (product, warehouse) that is at or below its applicable minimum. */
    @GetMapping("/low")
    public List<LowStockResponse> getLowStock(
            @RequestParam(required = false) Long warehouseId
    ) {
        return stockQueryService.getLowStock(warehouseId);
    }
}