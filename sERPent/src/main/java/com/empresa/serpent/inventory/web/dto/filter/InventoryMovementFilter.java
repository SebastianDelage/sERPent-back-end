package com.empresa.serpent.inventory.web.dto.filter;

import com.empresa.serpent.inventory.domain.enums.MovementType;
import java.time.LocalDateTime;

public record InventoryMovementFilter(
        Long productId,
        Long warehouseId,
        Long transactionId,
        MovementType movementType,
        LocalDateTime dateFrom,
        LocalDateTime dateTo
) {
}
