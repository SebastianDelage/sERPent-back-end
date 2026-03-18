package com.empresa.serpent.sync.web.dto.response;

public record PaymentMethodLiteDto(
        Long id,
        String name,
        Boolean active
) {}