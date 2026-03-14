package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

public interface InventoryReplenishmentProjection {

    Long getProductId();

    String getProductName();

    Long getWarehouseId();

    String getWarehouseName();

    BigDecimal getCurrentStock();

    BigDecimal getReorderPoint();

    BigDecimal getReorderQuantity();
}