package com.empresa.serpent.sync.web.dto.response;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;

import java.math.BigDecimal;

public record ProductLiteDto(
        Long id,
        String name,
        BigDecimal price,
        String sku,
        Boolean active,
        UnitOfMeasure unitOfMeasure
) {}