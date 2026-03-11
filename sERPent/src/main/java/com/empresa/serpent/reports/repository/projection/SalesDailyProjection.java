package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SalesDailyProjection {

    LocalDate getDate();

    Long getTransactions();

    BigDecimal getTotalRevenue();
}