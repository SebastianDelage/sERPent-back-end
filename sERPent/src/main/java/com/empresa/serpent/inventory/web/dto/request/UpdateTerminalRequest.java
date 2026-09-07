package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTerminalRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede tener más de {max} caracteres.")
        String name,

        @NotNull(message = "El depósito es obligatorio.")
        Long warehouseId,

        Boolean active
) {
}
