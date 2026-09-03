package com.empresa.serpent.inventory.web.dto.request;

import java.math.BigDecimal;

/**
 * NOT a request DTO despite living under {@code web/dto/request}: nothing deserialises this
 * from HTTP. {@code SaleApplicationService} builds it from an already-validated sale item and
 * hands it to {@code StockValidationService}.
 *
 * <p>Left without constraints on purpose. Bean validation only runs at the web boundary, so
 * annotations here would never be evaluated and would read as a guarantee that is not there —
 * which is exactly the kind of thing that made the quantity ceiling take five rounds to find.
 * The bound that matters is on the DTOs this is derived from.
 */
public record StockCheckItemRequest(
        Long productId,
        BigDecimal quantity
) {
}
