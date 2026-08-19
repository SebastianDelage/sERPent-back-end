package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

/**
 * One low-stock situation: a product short at a specific warehouse.
 *
 * <p>Deliberately one row per (product, warehouse) rather than per product. A product
 * missing at two branches is two situations to attend to, and summing branches before
 * comparing would hide a branch at zero behind another one that is overstocked.
 *
 * @param minimumStock       the threshold that actually applied, after the cascade
 * @param minimumFromWarehouse whether that threshold came from a per-warehouse override
 *                             ({@code true}) or from the product's own minimum
 * @param missingQuantity    how much is needed to reach the minimum; never negative
 */
public record LowStockResponse(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal currentStock,
        BigDecimal minimumStock,
        Boolean minimumFromWarehouse,
        BigDecimal missingQuantity
) {
}
