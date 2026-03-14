package com.empresa.serpent.reports.web.dto.response;

import com.empresa.serpent.inventory.domain.enums.MovementType;

import java.math.BigDecimal;

public record InventoryMovementsByTypeResponse(
        MovementType movementType,
        Long movements,
        BigDecimal totalQuantity
) {
}