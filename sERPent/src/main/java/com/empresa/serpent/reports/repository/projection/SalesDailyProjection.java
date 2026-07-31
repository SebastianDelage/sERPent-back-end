package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SalesDailyProjection {

    LocalDate getDate();

    /** Count of SALE transactions only; returns are not sales. */
    Long getTransactions();

    BigDecimal getGrossSales();

    /** Negative. A return falls on the day it was registered, not the day of the original sale. */
    BigDecimal getReturnsTotal();

    /** {@code grossSales + returnsTotal}; can be negative on a day of heavy returns. */
    BigDecimal getNetSales();
}
