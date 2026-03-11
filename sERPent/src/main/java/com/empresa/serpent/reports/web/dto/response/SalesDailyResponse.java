package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesDailyResponse(
        LocalDate date,
        Long transactions,
        BigDecimal totalRevenue
) {}