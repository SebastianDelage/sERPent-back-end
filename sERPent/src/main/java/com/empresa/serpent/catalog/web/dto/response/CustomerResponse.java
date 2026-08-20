package com.empresa.serpent.catalog.web.dto.response;

import java.time.LocalDateTime;

public record CustomerResponse(

        Long id,
        String name,
        String documentType,
        String documentNumber,
        String phone,
        Boolean active,
        LocalDateTime createdAt
) {}
