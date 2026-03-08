package com.empresa.serpent.inventory.web.dto.response;

import java.time.LocalDateTime;

public record WarehouseResponse(
        Long id,
        String name,
        Boolean active,
        LocalDateTime createdAt
) {
}