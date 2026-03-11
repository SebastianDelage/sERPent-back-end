package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

public interface SalesSummaryProjection {

    Long getTransactions();

    BigDecimal getTotalRevenue();

    BigDecimal getAverageTicket();
}