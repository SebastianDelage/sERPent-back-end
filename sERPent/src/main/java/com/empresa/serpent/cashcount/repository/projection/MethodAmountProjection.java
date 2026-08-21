package com.empresa.serpent.cashcount.repository.projection;

import java.math.BigDecimal;

/**
 * One payment method and one amount, as a shift-count query returns it.
 *
 * <p>Shared by every source that contributes to the expected figures, so they can all be
 * merged into one map by method without five nearly identical row types.
 */
public interface MethodAmountProjection {

    Long getPaymentMethodId();

    BigDecimal getAmount();
}
