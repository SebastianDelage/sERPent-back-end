package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

/** One product with its stock summed across warehouses, for the paginated stock view. */
public interface ProductStockProjection {

    Long getProductId();

    String getProductName();

    BigDecimal getTotalStock();
}
