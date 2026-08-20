package com.empresa.serpent.transactions.web.dto.response;

import java.math.BigDecimal;

/**
 * What a branch filter left out.
 *
 * <p>General expenses belong to the company rather than to any one branch, so filtering by
 * branch excludes them and the branches never add up to the total. Reporting that gap
 * explicitly is the point: without it the filtered list quietly understates spending and
 * whoever reads it has no way to know.
 *
 * @param count how many general expenses match the other active filters
 * @param total what they add up to
 */
public record GeneralExpensesSummaryResponse(
        long count,
        BigDecimal total
) {
}
