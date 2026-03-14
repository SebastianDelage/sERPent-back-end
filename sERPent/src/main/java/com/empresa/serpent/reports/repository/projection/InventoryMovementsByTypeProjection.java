package com.empresa.serpent.reports.repository.projection;

import com.empresa.serpent.inventory.domain.enums.MovementType;

import java.math.BigDecimal;

public interface InventoryMovementsByTypeProjection {

    MovementType getMovementType();

    Long getMovements();

    BigDecimal getTotalQuantity();
}