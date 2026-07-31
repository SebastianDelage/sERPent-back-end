package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

public interface SalesSummaryProjection {

    /** Count of SALE transactions only; returns are not sales. */
    Long getTransactions();

    BigDecimal getGrossSales();

    /** Negative: returns are stored as money going out. */
    BigDecimal getReturnsTotal();

    /** {@code grossSales + returnsTotal}. */
    BigDecimal getNetSales();

    /** Average over gross sales only. */
    BigDecimal getAverageTicket();
}
