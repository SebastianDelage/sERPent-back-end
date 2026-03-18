package com.empresa.serpent.sync.web.dto.request;

import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncCommandRequest(

        @NotBlank(message = "ClientId cannot be blank")
        String clientId,

        @NotBlank(message = "ClientOperationId cannot be blank")
        String clientOperationId,

        @NotNull(message = "CommandType cannot be null")
        SyncCommandType commandType,

        @NotBlank(message = "Payload cannot be blank")
        String payload
) {}