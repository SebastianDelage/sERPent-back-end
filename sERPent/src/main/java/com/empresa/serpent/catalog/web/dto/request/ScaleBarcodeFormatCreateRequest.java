package com.empresa.serpent.catalog.web.dto.request;

import com.empresa.serpent.catalog.domain.enums.ScaleValueType;
import jakarta.validation.constraints.*;

/**
 * Positions are 1-based from the left, matching how a scale manual numbers them.
 *
 * <p>The cross-field rules — every field inside the code, fields not overlapping each
 * other or the prefix, the check digit not sharing a position with a field — live in
 * ScaleBarcodeFormatService, because a message like "el valor se pisa con el código de
 * producto" needs both numbers to say anything useful.
 */
public record ScaleBarcodeFormatCreateRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 80, message = "El nombre no puede tener más de {max} caracteres.")
        String name,

        @NotBlank(message = "El prefijo es obligatorio.")
        @Pattern(regexp = "^\\d{1,4}$", message = "El prefijo tiene que ser de 1 a 4 dígitos.")
        String prefix,

        @NotNull(message = "La cantidad de dígitos de la etiqueta es obligatoria.")
        @Min(4) @Max(20)
        Integer totalLength,

        @NotNull(message = "La posición donde empieza el código de producto es obligatoria.")
        @Min(1)
        Integer productCodeStart,

        @NotNull(message = "La cantidad de dígitos del código de producto es obligatoria.")
        @Min(1)
        Integer productCodeLength,

        @NotNull(message = "La posición donde empieza el valor es obligatoria.")
        @Min(1)
        Integer valueStart,

        @NotNull(message = "La cantidad de dígitos del valor es obligatoria.")
        @Min(1)
        Integer valueLength,

        @NotNull(message = "Hay que indicar si el valor es peso o importe.")
        ScaleValueType valueType,

        @NotNull(message = "La cantidad de decimales del valor es obligatoria.")
        @Min(0) @Max(6)
        Integer valueDecimals,

        Boolean validateCheckDigit,

        Boolean active
) {}
