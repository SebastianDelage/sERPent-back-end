package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sales broken down by how they were collected, plus what was not collected at all.
 *
 * <p>THE INVARIANT THAT NO LONGER HOLDS: {@code sum(methods)} is NOT total sales. It was,
 * back when every sale named a payment method. Since sales can be taken on a customer's
 * account, a credit sale has no method and is absent from {@code methods} by construction
 * — which is the point, because putting money that never arrived into a payment-method row
 * would make the report lie about what came in.
 *
 * <p>{@code creditSales} is that missing piece, reported separately so the numbers
 * reconcile in the open: {@code sum(methods) + creditSales} is gross sales for the period.
 * Read {@code methods} as "what came in, and how", not as "everything that was sold".
 *
 * @param methods      one row per payment method actually used, returns excluded
 * @param collected    the sum of {@code methods}: money that did come in
 * @param creditSales  sold on account and NOT collected; it raised the customers' balances
 *                     instead, and will show up as cash only when they pay
 */
public record SalesByPaymentMethodReportResponse(
        List<SalesByPaymentMethodResponse> methods,
        BigDecimal collected,
        BigDecimal creditSales
) {}
