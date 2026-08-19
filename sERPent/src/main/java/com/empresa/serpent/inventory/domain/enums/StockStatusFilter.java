package com.empresa.serpent.inventory.domain.enums;

/**
 * Stock status filter for the stock screen.
 *
 * <p>{@link #BELOW_MINIMUM} resolves the minimum in cascade — the per-warehouse override
 * when one exists, the product's own minimum otherwise — and never matches a product
 * that has no minimum at either level.
 */
public enum StockStatusFilter {

    /** No status restriction. */
    ALL,

    /** Exactly zero (or less, if a correction ever drove it negative). */
    OUT_OF_STOCK,

    /** At or below the applicable minimum, same criterion as the low-stock report. */
    BELOW_MINIMUM,

    /** Anything above zero. */
    IN_STOCK
}
