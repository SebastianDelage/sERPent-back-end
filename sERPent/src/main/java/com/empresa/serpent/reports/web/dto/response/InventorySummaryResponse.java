package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record InventorySummaryResponse(
        Long productId,
        String productName,
        BigDecimal totalStock
) {
}