package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

public interface InventoryMovementsByProductProjection {

    Long getProductId();

    String getProductName();

    Long getMovements();

    BigDecimal getTotalIn();

    BigDecimal getTotalOut();

    BigDecimal getNetQuantity();
}