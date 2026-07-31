package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

/**
 * Sales totals for a period, split so returns are visible instead of silently
 * netted away.
 *
 * <p>{@code returns} is negative (that is how return transactions are stored), so
 * {@code netSales == grossSales + returns} is plain addition. {@code averageTicket}
 * is computed over gross sales only: a return registered today may belong to a sale
 * from another period, so folding it into the average would distort it.
 *
 * <p>{@code totalRevenue} is kept as an alias of {@code netSales} for existing
 * dashboard consumers.
 */
public record SalesSummaryResponse(
        Long transactions,
        BigDecimal grossSales,
        BigDecimal returns,
        BigDecimal netSales,
        BigDecimal totalRevenue,
        BigDecimal averageTicket
) {
}
