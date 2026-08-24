package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One (product, warehouse) that has reached its reorder point, with everything needed to
 * act on it: how short it is, who to buy from, and what it cost last time.
 *
 * <p>All three thresholds are the ones that APPLY AT THIS WAREHOUSE, already resolved
 * through the cascade — the warehouse's override where it has one, the product's otherwise.
 * The caller never has to resolve anything.
 *
 * @param minimumStock            the floor at this warehouse. Reported alongside the trigger
 *                                because the distance between them is what says how urgent
 *                                this line is: at the reorder point there is still cover, at
 *                                the minimum there is not.
 * @param reorderPoint            the trigger that fired at this warehouse
 * @param suggestedOrderQuantity  how much to order. Null when no reorder quantity is defined
 *                                at either level — the product is short and we say so, but
 *                                nobody has said how much to buy, and inventing a number
 *                                would be worse than leaving it blank.
 * @param preferredSupplierName   who to buy from, or null when the product has no preferred
 *                                supplier. A product with no supplier still appears: it is
 *                                short either way, and knowing that is the point.
 * @param supplierProductCode     the preferred supplier's own code for this product
 * @param lastPurchaseUnitPrice   what the last confirmed purchase of this product cost
 * @param lastPurchaseSupplierName the supplier of THAT purchase, which may differ from the
 *                                preferred one. Reported so the price is never read as the
 *                                preferred supplier's price when it is somebody else's.
 */
public record InventoryReplenishmentResponse(

        Long productId,
        String productName,
        String productSku,

        Long warehouseId,
        String warehouseName,

        BigDecimal currentStock,
        BigDecimal minimumStock,
        BigDecimal reorderPoint,
        BigDecimal reorderQuantity,
        BigDecimal suggestedOrderQuantity,

        Long preferredSupplierId,
        String preferredSupplierName,
        String supplierProductCode,
        Integer leadTimeDays,

        BigDecimal lastPurchaseUnitPrice,
        LocalDateTime lastPurchaseDate,
        String lastPurchaseSupplierName
) {}
