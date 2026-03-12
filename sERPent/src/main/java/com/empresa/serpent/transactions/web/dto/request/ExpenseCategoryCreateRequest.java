package com.empresa.serpent.transactions.web.dto.request;


import jakarta.validation.constraints.NotBlank;

public record ExpenseCategoryCreateRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        String description,

        Boolean active
) {
}