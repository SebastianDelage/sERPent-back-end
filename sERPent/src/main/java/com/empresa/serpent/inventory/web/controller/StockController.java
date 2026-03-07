package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import lombok.RequiredArgsConstructor;
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
}
