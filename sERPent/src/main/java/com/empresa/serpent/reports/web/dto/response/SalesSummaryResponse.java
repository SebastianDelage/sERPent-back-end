package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

/**
 * Sales totals for a period, broken down so every adjustment that moved the money is
 * visible rather than silently folded into one figure.
 *
 * <p>The parts add up:
 * {@code listPriceSales + paymentMethodSurcharges + manualAdjustments + returns == netSales}.
 * Each middle term is signed, so the sum is plain addition: the mechanism-2 rule and
 * the manual adjustment can each surcharge or discount, and {@code returns} is
 * negative because that is how return transactions are stored.
 *
 * <p>{@code listPriceSales} is what the goods would have cost at catalog price. It
 * replaces the earlier {@code grossSales}, which summed line subtotals that already
 * carried the payment-method surcharge and so was neither list price nor net.
 *
 * <p>{@code averageTicket} is computed over sale totals only: a return registered
 * today may belong to a sale from another period, so folding it in would distort it.
 *
 * <p>{@code totalRevenue} is kept as an alias of {@code netSales} for existing
 * dashboard consumers.
 */
public record SalesSummaryResponse(
        Long transactions,
        BigDecimal listPriceSales,
        BigDecimal paymentMethodSurcharges,
        BigDecimal manualAdjustments,
        BigDecimal returns,
        BigDecimal netSales,
        BigDecimal totalRevenue,
        BigDecimal averageTicket
) {
}
