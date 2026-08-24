package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

/**
 * One (product, warehouse) that has fallen to its reorder point.
 *
 * <p>The three thresholds come out already resolved through the cascade: the query
 * COALESCEs the warehouse override over the product's own figure, so nothing downstream has
 * to know the fallback rule.
 */
public interface InventoryReplenishmentProjection {

    Long getProductId();

    String getProductName();

    String getProductSku();

    Long getWarehouseId();

    String getWarehouseName();

    BigDecimal getCurrentStock();

    /** The floor that applies at THIS warehouse, override or inherited. */
    BigDecimal getMinimumStock();

    /** The trigger that applies at THIS warehouse, override or inherited. */
    BigDecimal getReorderPoint();

    /** How much to order at THIS warehouse, override or inherited. Null when undefined. */
    BigDecimal getReorderQuantity();
}