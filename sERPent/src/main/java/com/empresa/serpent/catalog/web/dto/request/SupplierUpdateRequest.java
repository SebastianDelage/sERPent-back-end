package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        @Size(max = 30, message = "Document type cannot be longer than 30 characters")
        String documentType,

        @Size(max = 40, message = "Document number cannot be longer than 40 characters")
        String documentNumber,

        @Size(max = 50, message = "Tax condition cannot be longer than 50 characters")
        String taxCondition,

        @Size(max = 50, message = "Phone cannot be longer than 50 characters")
        String phone,

        @Email(message = "Email must be a valid email address")
        @Size(max = 150, message = "Email cannot be longer than 150 characters")
        String email,

        String notes,
        String address,
        Boolean active
) {}