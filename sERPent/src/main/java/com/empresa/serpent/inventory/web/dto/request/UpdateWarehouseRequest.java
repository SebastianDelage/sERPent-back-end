package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateWarehouseRequest(
        @NotBlank String name,
        Boolean active
) {
}
