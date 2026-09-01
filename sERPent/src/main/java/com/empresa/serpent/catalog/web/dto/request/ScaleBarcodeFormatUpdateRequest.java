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
public record ScaleBarcodeFormatUpdateRequest(

        @NotBlank(message = "Name cannot be blank")
        @Size(max = 80, message = "Name cannot be longer than 80 characters")
        String name,

        @NotBlank(message = "Prefix cannot be blank")
        @Pattern(regexp = "^\\d{1,4}$", message = "Prefix must be 1 to 4 digits")
        String prefix,

        @NotNull(message = "Total length cannot be null")
        @Min(4) @Max(20)
        Integer totalLength,

        @NotNull(message = "Product code start cannot be null")
        @Min(1)
        Integer productCodeStart,

        @NotNull(message = "Product code length cannot be null")
        @Min(1)
        Integer productCodeLength,

        @NotNull(message = "Value start cannot be null")
        @Min(1)
        Integer valueStart,

        @NotNull(message = "Value length cannot be null")
        @Min(1)
        Integer valueLength,

        @NotNull(message = "Value type cannot be null")
        ScaleValueType valueType,

        @NotNull(message = "Value decimals cannot be null")
        @Min(0) @Max(6)
        Integer valueDecimals,

        Boolean validateCheckDigit,

        Boolean active
) {}
