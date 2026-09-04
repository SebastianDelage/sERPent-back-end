package com.empresa.serpent.inventory.web.dto.request;

import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Defines (or redefines) a product's reorder configuration at one warehouse.
 *
 * <p>All three figures are optional and INDEPENDENT: send only the ones this warehouse
 * overrides, and leave the rest null to keep inheriting the product's. Sending all three
 * null is how you say "inherit everything", and the service deletes the override row
 * rather than storing one that overrides nothing.
 *
 * <p>WAREHOUSE ceilings and not counter ones, even though these three live on a product's
 * form. They are a warehouse's restocking configuration — its floor, when it reorders and
 * how much it orders — so they are sized by what arrives on a pallet, not by what a cashier
 * types with people waiting. See {@link QuantityLimits}.
 *
 * <p>These were the last quantity fields in the app with no upper bound: the front end
 * drives them with a one-way {@code [ngModel]} rather than a {@code FormControl}, so the
 * {@code Validators.max} that covers the other six could not reach them, and this DTO had
 * {@code @PositiveOrZero} but no {@code @Digits}. The only ceiling was the column's
 * {@code NUMERIC(12,3)}, which is a storage limit and not a rule anyone can read.
 */
public record UpsertProductWarehouseMinimumStockRequest(

        @NotNull(message = "El depósito es obligatorio.")
        Long warehouseId,

        /** This warehouse's floor, or null to inherit the product's. */
        @PositiveOrZero(message = "El mínimo no puede ser negativo.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "El mínimo no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal minimumStock,

        /** When this warehouse reorders, or null to inherit the product's reorder point. */
        @PositiveOrZero(message = "El punto de reposición no puede ser negativo.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "El punto de reposición no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal reorderPoint,

        /** How much this warehouse orders, or null to inherit the product's quantity. */
        @PositiveOrZero(message = "La cantidad de reposición no puede ser negativa.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad de reposición no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal reorderQuantity
) {
}
