package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.ProductWarehouseMinimumStockService;
import com.empresa.serpent.inventory.web.dto.request.UpsertProductWarehouseMinimumStockRequest;
import com.empresa.serpent.inventory.web.dto.response.ProductWarehouseMinimumStockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Per-warehouse minimum stock for a product. Scoped under the product because an
 * override has no meaning on its own — it is always an exception to that product's
 * minimum.
 */
@RestController
@RequestMapping("/api/products/{productId}/warehouse-minimum-stock")
@RequiredArgsConstructor
public class ProductWarehouseMinimumStockController {

    private final ProductWarehouseMinimumStockService service;

    /** Every active warehouse with the minimum that applies, override or inherited. */
    @GetMapping
    public List<ProductWarehouseMinimumStockResponse> findByProduct(@PathVariable Long productId) {
        return service.findByProduct(productId);
    }

    @PutMapping
    public ProductWarehouseMinimumStockResponse upsert(
            @PathVariable Long productId,
            @Valid @RequestBody UpsertProductWarehouseMinimumStockRequest request
    ) {
        return service.upsert(productId, request);
    }

    /** Removes the override: the warehouse goes back to the product's own minimum. */
    @DeleteMapping("/{warehouseId}")
    public void delete(@PathVariable Long productId, @PathVariable Long warehouseId) {
        service.delete(productId, warehouseId);
    }
}
