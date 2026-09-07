package com.empresa.serpent.transactions.web.dto.request;


import jakarta.validation.constraints.NotBlank;

public record UpdateExpenseCategoryRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        String name,

        String description,

        Boolean active
) {
}