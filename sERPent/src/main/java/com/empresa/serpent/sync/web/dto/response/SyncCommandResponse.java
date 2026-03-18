package com.empresa.serpent.sync.web.dto.response;

import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;

public record SyncCommandResponse(

        String clientOperationId,
        SyncCommandStatus status,
        SyncResultReferenceType resultReferenceType,
        Long resultReferenceId,
        String message
) {}