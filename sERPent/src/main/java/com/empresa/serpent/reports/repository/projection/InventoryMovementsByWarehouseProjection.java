package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

public interface InventoryMovementsByWarehouseProjection {

    Long getWarehouseId();

    String getWarehouseName();

    Long getMovements();

    BigDecimal getTotalIn();

    BigDecimal getTotalOut();

    BigDecimal getNetQuantity();
}