package com.empresa.serpent.inventory.web.dto.response;

import com.empresa.serpent.inventory.domain.enums.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryMovementResponse(
        Long id,
        MovementType movementType,
        BigDecimal quantity,
        BigDecimal unitCost,
        LocalDateTime createdAt,
        String note,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        Long transactionId
) {
}
