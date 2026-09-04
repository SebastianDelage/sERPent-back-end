package com.empresa.serpent.catalog.web.dto.request;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;
import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates a product.
 *
 * <p>WAREHOUSE ceilings on the three reorder figures and not counter ones. They are
 * restocking configuration — the product's floor, when it reorders and how much it orders —
 * so they are sized by what arrives on a pallet, not by what a cashier types with people
 * waiting. They are also the defaults every warehouse inherits, so they have to be able to
 * hold whatever the widest warehouse would set. See {@link QuantityLimits}.
 *
 * <p>The per-warehouse overrides of these same three figures live in
 * {@code UpsertProductWarehouseMinimumStockRequest} and carry the same ceilings.
 */
public record ProductCreateRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        String name,

        String description,

        @NotNull(message = "El precio es obligatorio.")
        @PositiveOrZero(message = "El precio no puede ser negativo.")
        BigDecimal price,

        @Size(max = 80, message = "El SKU no puede tener más de 80 caracteres.")
        String sku,

        @Pattern(
                regexp = "^(\\d{8}|\\d{12}|\\d{13})?$",
                message = "El código de barras tiene que tener 8, 12 o 13 dígitos (EAN-8, UPC-A o EAN-13)."
        )
        String barcode,

        @Pattern(
                regexp = "^(\\d{1,20})?$",
                message = "El código de balanza solo admite dígitos."
        )
        String scaleCode,

        Boolean active,

        /** El piso del producto, que cada depósito hereda si no define el suyo. */
        @PositiveOrZero(message = "El stock mínimo no puede ser negativo.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "El stock mínimo no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal minimumStock,

        /** Cuándo se repone, por defecto para todos los depósitos. */
        @PositiveOrZero(message = "El punto de reorden no puede ser negativo.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "El punto de reorden no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal reorderPoint,

        /** Cuánto se pide, por defecto para todos los depósitos. */
        @PositiveOrZero(message = "La cantidad de reorden no puede ser negativa.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad de reorden no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal reorderQuantity,

        @NotNull(message = "La unidad de medida es obligatoria.")
        UnitOfMeasure unitOfMeasure
) {}
