package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record SalesSummaryResponse(
        Long transactions,
        BigDecimal totalRevenue,
        BigDecimal averageTicket
) {
}