package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

/**
 * The reorder configuration that applies to a product at one warehouse, after the cascade.
 *
 * <p>Returned for EVERY active warehouse, not only the ones with an override, so the caller
 * can show the effective figures everywhere without resolving the cascade itself.
 *
 * <p>Each {@code own*} field distinguishes the two cases for its own figure: when null, that
 * particular figure is inherited and the matching {@code effective*} is the product's. The
 * three cascade independently, so a warehouse can perfectly well own its reorder point while
 * inheriting its minimum.
 *
 * @param effectiveMinimum   the floor in force; null when the product has no minimum at
 *                           either level, meaning it is never reported as low
 * @param effectiveReorderPoint when to order; null when there is none at either level,
 *                           meaning it never appears in the replenishment report
 */
public record ProductWarehouseMinimumStockResponse(
        Long warehouseId,
        String warehouseName,

        BigDecimal ownMinimum,
        BigDecimal effectiveMinimum,

        BigDecimal ownReorderPoint,
        BigDecimal effectiveReorderPoint,

        BigDecimal ownReorderQuantity,
        BigDecimal effectiveReorderQuantity,

        /** True when this warehouse has no override row at all: every figure is the product's. */
        Boolean inherited
) {
}
