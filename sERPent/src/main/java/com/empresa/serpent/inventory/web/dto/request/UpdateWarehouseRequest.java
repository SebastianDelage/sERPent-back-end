package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateWarehouseRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,
        Boolean active
) {
}