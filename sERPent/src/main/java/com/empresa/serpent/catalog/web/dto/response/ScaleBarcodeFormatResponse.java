package com.empresa.serpent.catalog.web.dto.response;

import com.empresa.serpent.catalog.domain.enums.ScaleValueType;

import java.time.LocalDateTime;

public record ScaleBarcodeFormatResponse(
        Long id,
        String name,
        String prefix,
        Integer totalLength,
        Integer productCodeStart,
        Integer productCodeLength,
        Integer valueStart,
        Integer valueLength,
        ScaleValueType valueType,
        Integer valueDecimals,
        Boolean validateCheckDigit,
        Boolean active,
        LocalDateTime createdAt
) {}
