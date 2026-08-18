package com.empresa.serpent.inventory.web.dto.response;

import java.time.LocalDateTime;

public record TerminalResponse(
        Long id,
        String name,
        Long warehouseId,
        String warehouseName,
        Boolean active,
        LocalDateTime createdAt
) {
}
