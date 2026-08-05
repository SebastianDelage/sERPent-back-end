package com.empresa.serpent.transactions.domain.enums;

/**
 * How a manual adjustment on a sale total is expressed.
 *
 * <p>Direction (discount vs surcharge) is not encoded here: it is carried by the
 * sign of the value itself, so there is a single source of truth. A negative value
 * discounts, a positive value surcharges.
 */
public enum AdjustmentType {
    NONE,
    PERCENTAGE,
    FIXED
}
