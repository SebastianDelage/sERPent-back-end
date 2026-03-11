package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record SalesByProductResponse(
        Long productId,
        String productName,
        BigDecimal quantitySold,
        BigDecimal totalRevenue
) {
}