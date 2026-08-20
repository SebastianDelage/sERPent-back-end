package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(

        @NotBlank(message = "Name cannot be blank")
        @Size(max = 150, message = "Name cannot be longer than 150 characters")
        String name,

        @Size(max = 30, message = "Document type cannot be longer than 30 characters")
        String documentType,

        @Size(max = 40, message = "Document number cannot be longer than 40 characters")
        String documentNumber,

        @Size(max = 50, message = "Phone cannot be longer than 50 characters")
        String phone,

        Boolean active
) {}
