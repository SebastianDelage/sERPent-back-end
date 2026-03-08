package com.empresa.serpent.catalog.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        Boolean active,
        LocalDateTime createdAt
) {
}