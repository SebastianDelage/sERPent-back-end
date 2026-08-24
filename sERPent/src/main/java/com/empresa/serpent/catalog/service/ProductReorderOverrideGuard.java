package com.empresa.serpent.catalog.service;

import java.math.BigDecimal;

/**
 * Lets {@code ProductService} check its reorder figures against the per-warehouse overrides
 * without importing {@code inventory}.
 *
 * <p>WHY THIS INTERFACE EXISTS, given there is exactly one implementation: the overrides
 * live in {@code inventory}, and the dependency between the two packages runs one way only —
 * {@code inventory} knows about {@code catalog}, never the reverse. Injecting the override
 * service straight into {@code ProductService} would turn that into a cycle. Declaring the
 * port here and implementing it there keeps the arrow pointing the same way it always did.
 *
 * <p>The rule being protected: at any warehouse, the reorder point that applies may not sit
 * below the minimum that applies. A product's own figures are the fallback for every
 * warehouse that does not override them, so editing them can break a warehouse that is
 * consistent and is not itself being edited.
 */
public interface ProductReorderOverrideGuard {

    /**
     * Refuses the change when it would leave some warehouse with a reorder point below its
     * minimum, once the cascade is resolved with the proposed product-level figures.
     *
     * @throws com.empresa.serpent.shared.exception.ValidationException naming the warehouses
     */
    void validateOverridesAgainst(Long productId,
                                  BigDecimal productMinimumStock,
                                  BigDecimal productReorderPoint);
}
