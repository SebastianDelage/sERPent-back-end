package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTerminalRequest(

        @NotBlank(message = "Name cannot be blank")
        @Size(max = 120, message = "Name cannot be longer than 120 characters")
        String name,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        Boolean active
) {
}
