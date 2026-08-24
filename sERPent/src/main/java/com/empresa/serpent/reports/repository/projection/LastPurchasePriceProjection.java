package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What we last paid for a product, and when.
 *
 * <p>Derived from the purchase lines rather than stored on the product↔supplier link, so
 * there is exactly one number and it cannot disagree with the purchases it came from.
 *
 * <p>Carries the supplier it came from because that may NOT be the preferred one: the report
 * proposes who to buy from next, this says what the last purchase actually cost. Showing the
 * price without the name it belongs to would read as the preferred supplier's price.
 */
public interface LastPurchasePriceProjection {

    Long getProductId();

    BigDecimal getUnitPrice();

    LocalDateTime getPurchaseDate();

    /** Null on purchases loaded without naming a supplier, which the API allows. */
    String getSupplierName();
}
