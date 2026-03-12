package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record WarehouseSummaryResponse(
        Long warehouseId,
        String warehouseName,
        Long distinctProducts,
        BigDecimal totalUnits
) {
}