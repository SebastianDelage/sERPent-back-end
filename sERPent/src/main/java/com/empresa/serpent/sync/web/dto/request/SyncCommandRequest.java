package com.empresa.serpent.sync.web.dto.request;

import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncCommandRequest(

        @NotBlank(message = "El identificador del cliente es obligatorio.")
        String clientId,

        @NotBlank(message = "El identificador de la operación es obligatorio.")
        String clientOperationId,

        @NotNull(message = "El tipo de comando es obligatorio.")
        SyncCommandType commandType,

        @NotBlank(message = "El contenido del comando es obligatorio.")
        String payload
) {}