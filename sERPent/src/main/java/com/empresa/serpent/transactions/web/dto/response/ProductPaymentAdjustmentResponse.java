package com.empresa.serpent.transactions.web.dto.response;

import java.math.BigDecimal;

public record ProductPaymentAdjustmentResponse(
        Long id,
        Long productId,
        String productName,
        Long paymentMethodId,
        String paymentMethodName,
        /** Signed: negative discounts, positive surcharges. */
        BigDecimal adjustmentPercentage,
        Boolean active
) {}
