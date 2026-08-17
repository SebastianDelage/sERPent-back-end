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
 *
 * <p><strong>{@code grossSales} here is not the summary's {@code listPriceSales}.</strong>
 * This one sums sale totals, which already carry the per-line payment-method surcharge
 * and the sale-wide manual adjustment; the summary's figure is the catalog price before
 * either. They are different quantities under similar names — do not add or compare them
 * across the two reports. Giving the daily the same breakdown is pending work.
 */
public record SalesDailyResponse(
        LocalDate date,
        Long transactions,
        BigDecimal grossSales,
        BigDecimal returns,
        BigDecimal netSales,
        BigDecimal totalRevenue
) {}
