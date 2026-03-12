package com.empresa.serpent.transactions.web.dto.response;

public record ExpenseCategoryResponse(
        Long id,
        String name,
        String description,
        Boolean active
) {
}