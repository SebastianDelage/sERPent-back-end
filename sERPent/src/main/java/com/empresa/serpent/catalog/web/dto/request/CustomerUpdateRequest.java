package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede tener más de {max} caracteres.")
        String name,

        @Size(max = 30, message = "El tipo de documento no puede tener más de {max} caracteres.")
        String documentType,

        @Size(max = 40, message = "El número de documento no puede tener más de {max} caracteres.")
        String documentNumber,

        @Size(max = 50, message = "El teléfono no puede tener más de {max} caracteres.")
        String phone,

        Boolean active
) {}
