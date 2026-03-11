package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SupplierUpdateRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        String documentType,
        String documentNumber,
        String taxCondition,
        String phone,
        String email,
        Boolean active
) {}