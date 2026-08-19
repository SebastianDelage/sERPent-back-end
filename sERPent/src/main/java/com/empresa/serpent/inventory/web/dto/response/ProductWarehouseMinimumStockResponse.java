package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

/**
 * The minimum stock that applies to a product at one warehouse, after the cascade.
 *
 * <p>Returned for EVERY active warehouse, not only the ones with an override, so the
 * caller can show the effective threshold everywhere without resolving the cascade
 * itself. {@code ownMinimum} distinguishes the two cases: when null the warehouse has no
 * override and {@code effectiveMinimum} is the product's own minimum, inherited.
 *
 * @param effectiveMinimum the threshold in force; null when the product has no minimum
 *                         at either level, meaning it is never reported as low
 */
public record ProductWarehouseMinimumStockResponse(
        Long warehouseId,
        String warehouseName,
        BigDecimal ownMinimum,
        BigDecimal effectiveMinimum,
        Boolean inherited
) {
}
