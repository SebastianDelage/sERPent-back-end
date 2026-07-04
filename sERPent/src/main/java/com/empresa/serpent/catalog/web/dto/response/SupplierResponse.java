package com.empresa.serpent.catalog.web.dto.response;

import java.time.LocalDateTime;

public record SupplierResponse(

        Long id,
        String name,
        String documentType,
        String documentNumber,
        String taxCondition,
        String phone,
        String email,
        String address,
        String notes,
        Boolean active,
        LocalDateTime createdAt
) {}