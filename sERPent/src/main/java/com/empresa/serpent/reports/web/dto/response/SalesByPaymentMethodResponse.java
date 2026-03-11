package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record SalesByPaymentMethodResponse(
        Long paymentMethodId,
        String paymentMethodName,
        Long transactions,
        BigDecimal totalRevenue
) {
}