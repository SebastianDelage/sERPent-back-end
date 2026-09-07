package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateWarehouseRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        String name,
        Boolean active
) {
}