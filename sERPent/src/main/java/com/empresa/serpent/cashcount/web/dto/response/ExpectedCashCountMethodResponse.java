package com.empresa.serpent.cashcount.web.dto.response;

import java.math.BigDecimal;

/**
 * One payment method's expected amount, broken into the parts that add up to it.
 *
 * <p>The breakdown is the point: a cashier who sees only "esperado: 47.300" has no way to
 * check it, but one who sees the sales, the collections and what left the drawer can find
 * the missing receipt themselves.
 *
 * <p>{@code expectedAmount == openingFloat + sales + customerPayments + returns
 * - supplierPayments - expenses - purchases}, where {@code returns} is already negative and
 * the three subtracted figures are reported as positive amounts.
 *
 * @param openingFloat     cash left to make change. Zero on every method except cash.
 * @param returns          money handed back. Already negative, so it adds.
 * @param supplierPayments money paid to suppliers. Positive here, subtracted in the total.
 * @param expenses         expenses paid from this branch. Positive here, subtracted.
 * @param purchases        stock paid for on the spot. Positive here, subtracted.
 */
public record ExpectedCashCountMethodResponse(
        Long paymentMethodId,
        String paymentMethodName,
        boolean isCash,
        BigDecimal openingFloat,
        BigDecimal sales,
        BigDecimal customerPayments,
        BigDecimal returns,
        BigDecimal supplierPayments,
        BigDecimal expenses,
        BigDecimal purchases,
        BigDecimal expectedAmount
) {
}
