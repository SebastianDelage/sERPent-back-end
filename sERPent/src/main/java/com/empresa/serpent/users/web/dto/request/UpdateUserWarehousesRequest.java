package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replaces a user's warehouse assignment wholesale. */
public record UpdateUserWarehousesRequest(

        @NotEmpty(message = "El usuario tiene que tener al menos un depósito asignado.")
        List<Long> warehouseIds
) {
}
