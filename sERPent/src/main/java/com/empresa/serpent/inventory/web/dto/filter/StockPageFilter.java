package com.empresa.serpent.inventory.web.dto.filter;

import com.empresa.serpent.inventory.domain.enums.StockStatusFilter;

/**
 * Filters for the paginated stock screen.
 *
 * <p>Deliberately separate from {@link StockFilter}, which serves the non-paginated
 * lookups the operational forms rely on (sale, adjustment, transformation, transfer all
 * need a warehouse's full stock list, not a page of it). Merging the two would drag a
 * grid's concerns into those call sites for no benefit.
 *
 * @param search matches product name (partial), SKU (exact) or barcode (exact)
 */
public record StockPageFilter(
        String search,
        Long warehouseId,
        StockStatusFilter status
) {

    /** The status actually in force; absent means no restriction. */
    public StockStatusFilter statusOrAll() {
        return status == null ? StockStatusFilter.ALL : status;
    }
}
