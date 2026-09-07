package com.empresa.serpent.catalog.web.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierCreateRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        String name,

        @Size(max = 30, message = "El tipo de documento no puede tener más de {max} caracteres.")
        String documentType,

        @Size(max = 40, message = "El número de documento no puede tener más de {max} caracteres.")
        String documentNumber,

        @Size(max = 50, message = "La condición frente al IVA no puede tener más de {max} caracteres.")
        String taxCondition,

        @Size(max = 50, message = "El teléfono no puede tener más de {max} caracteres.")
        String phone,

        @Email(message = "El email no tiene un formato válido.")
        @Size(max = 150, message = "El email no puede tener más de {max} caracteres.")
        String email,

        String notes,
        String address,
        Boolean active
) {}