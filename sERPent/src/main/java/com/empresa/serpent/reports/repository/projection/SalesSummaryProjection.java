package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

/**
 * The summary's raw figures, split so every adjustment that moved the money is
 * visible instead of folded into one number.
 *
 * <p>They add up: {@code listPriceSales + paymentMethodSurcharges + manualAdjustments
 * + returnsTotal == netSales}. That identity holds exactly, not approximately: the
 * line-level figures are anchored on the stored {@code subtotal}, so the two halves
 * of each line sum back to it by algebra rather than by rounding luck.
 */
public interface SalesSummaryProjection {

    /** Count of SALE transactions only; returns are not sales. */
    Long getTransactions();

    /** Sale lines at catalog price, before any adjustment. */
    BigDecimal getListPriceSales();

    /** Signed: the mechanism-2 rule can surcharge or discount a line. */
    BigDecimal getPaymentMethodSurcharges();

    /** Signed: the sale-wide manual adjustment can be a discount or a surcharge. */
    BigDecimal getManualAdjustments();

    /** Negative: returns are stored as money going out. */
    BigDecimal getReturnsTotal();

    /** What actually came in, across sales and returns. */
    BigDecimal getNetSales();

    /** Average over sale totals only. */
    BigDecimal getAverageTicket();
}
