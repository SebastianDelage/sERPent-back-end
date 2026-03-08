package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateWarehouseRequest(
         @NotBlank String name,
         Boolean active
) {
}
