package com.empresa.serpent.catalog.web.dto.response;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        Boolean active,
        BigDecimal minimumStock,
        BigDecimal reorderPoint,
        BigDecimal reorderQuantity,
        LocalDateTime createdAt,
        UnitOfMeasure unitOfMeasure
) {
}