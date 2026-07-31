package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row per day. A return counts on the day it was registered, not on the day of
 * the original sale, so {@code netSales} can be negative on a day whose returns
 * outweigh its sales.
 *
 * <p>{@code totalRevenue} is kept as an alias of {@code netSales} for existing
 * dashboard consumers.
 */
public record SalesDailyResponse(
        LocalDate date,
        Long transactions,
        BigDecimal grossSales,
        BigDecimal returns,
        BigDecimal netSales,
        BigDecimal totalRevenue
) {}
