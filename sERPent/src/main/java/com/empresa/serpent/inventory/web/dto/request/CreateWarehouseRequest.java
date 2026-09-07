package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateWarehouseRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        String name,
        Boolean active
) {
}