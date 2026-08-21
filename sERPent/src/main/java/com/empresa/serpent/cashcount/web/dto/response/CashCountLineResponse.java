package com.empresa.serpent.cashcount.web.dto.response;

import java.math.BigDecimal;

/**
 * One method's line of a stored count.
 *
 * <p>The name and the cash flag are the ones frozen at close time, not today's: a method
 * renamed since then still reads here as what it was called when it was counted.
 */
public record CashCountLineResponse(
        Long paymentMethodId,
        String paymentMethodName,
        boolean isCash,
        BigDecimal expectedAmount,
        BigDecimal countedAmount,

        /** counted - expected. Negative means the till came up short. */
        BigDecimal differenceAmount
) {
}
