package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

/**
 * Per-product sales for a period, split so returns stay visible.
 *
 * <p>Quantities are magnitudes (both positive); amounts carry their sign, so
 * {@code returnedAmount} is negative and {@code netRevenue == grossRevenue +
 * returnedAmount}. A return counts on the day it was registered, which means a
 * product can show a return in a period where it records no sale.
 *
 * <p>{@code totalRevenue} is kept as an alias of {@code netRevenue} for existing
 * dashboard consumers.
 */
public record SalesByProductResponse(
        Long productId,
        String productName,
        BigDecimal quantitySold,
        BigDecimal quantityReturned,
        BigDecimal grossRevenue,
        BigDecimal returnedAmount,
        BigDecimal netRevenue,
        BigDecimal totalRevenue
) {
}
