package com.empresa.serpent.catalog.domain.enums;

/**
 * What the numeric field of a scale label carries.
 *
 * <p>The prefix cannot tell us this: GS1 reserves prefix 2 for the shop's own use and
 * leaves the layout entirely open, so every brand does it differently. It is part of the
 * per-shop format configuration instead.
 */
public enum ScaleValueType {

    /** A weight. Combined with valueDecimals it always resolves to KILOGRAMS. */
    WEIGHT,

    /** A money amount. Combined with valueDecimals it resolves to the shop's currency. */
    AMOUNT
}
