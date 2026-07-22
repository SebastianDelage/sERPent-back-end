package com.empresa.serpent.sync.service;

import com.empresa.serpent.shared.exception.BusinessException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import com.empresa.serpent.sync.web.dto.request.SyncCommandRequest;
import com.empresa.serpent.sync.web.dto.response.SyncCommandResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SyncCommandApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SyncCommandApplicationService.class);

    // Same generic Spanish text GlobalExceptionHandler already uses for direct REST calls — keeps
    // the sync response consistent with the rest of the API instead of leaking the raw, English,
    // id-bearing exception message that only makes sense in the logs.
    private static final String NOT_FOUND_MESSAGE = "No se encontró el recurso solicitado.";
    private static final String INVALID_REQUEST_MESSAGE = "La solicitud no es válida.";

    private final ClientSyncCommandRepository repository;
    private final ClientSyncCommandPersistenceService persistenceService;
    private final SyncCommandResultService resultService;

    public SyncCommandResponse process(SyncCommandRequest request) {

        Optional<ClientSyncCommandEntity> existing =
                repository.findByClientIdAndClientOperationId(
                        request.clientId(),
                        request.clientOperationId()
                );

        ClientSyncCommandEntity command;

        if (existing.isPresent()) {
            command = existing.get();

            // Only a terminal command is a real duplicate. A command left in RECEIVED (an
            // interrupted attempt whose operation never committed) or FAILED (a retriable
            // technical failure) is reprocessed reusing the same row.
            if (isTerminal(command.getStatus())) {
                return duplicateResponse(command);
            }
        } else {
            Optional<ClientSyncCommandEntity> inserted = insertReceived(request);

            if (inserted.isEmpty()) {
                // A concurrent request inserted the same (clientId, clientOperationId) first.
                // Resolve the unique-constraint collision as DUPLICATE, never as FAILED.
                return repository
                        .findByClientIdAndClientOperationId(request.clientId(), request.clientOperationId())
                        .map(this::duplicateResponse)
                        .orElseThrow(() -> new IllegalStateException(
                                "Sync command disappeared after a unique constraint violation"));
            }

            command = inserted.get();
        }

        return dispatch(command);
    }

    private Optional<ClientSyncCommandEntity> insertReceived(SyncCommandRequest request) {
        ClientSyncCommandEntity command = ClientSyncCommandEntity.builder()
                .clientId(request.clientId())
                .clientOperationId(request.clientOperationId())
                .commandType(request.commandType())
                .status(SyncCommandStatus.RECEIVED)
                .payload(request.payload())
                .build();

        try {
            // Committed in its own transaction (REQUIRES_NEW) so the row exists for dedup even if
            // the later processing transaction rolls back.
            return Optional.of(persistenceService.save(command));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
    }

    private SyncCommandResponse dispatch(ClientSyncCommandEntity command) {
        try {
            return switch (command.getCommandType()) {
                case CREATE_SALE -> resultService.processCreateSale(command.getId());
                case CREATE_EXPENSE -> resultService.processCreateExpense(command.getId());
            };
        } catch (BusinessException ex) {
            // ValidationException / ConflictException / InsufficientStockException already carry a
            // clean, user-facing Spanish message (verified case by case) — safe to return as-is,
            // same guarantee GlobalExceptionHandler relies on for direct REST calls.
            return markTerminalFailure(command, SyncCommandStatus.REJECTED, ex.getMessage());
        } catch (NotFoundException | IllegalArgumentException ex) {
            // These carry English text with raw technical ids (e.g. "Product not found: 5").
            // SyncCommandResponse.message bypasses GlobalExceptionHandler entirely, so sanitize
            // here too: clean generic message to the client, full detail to the log.
            log.warn(
                    "Sync command rejected for client {} operation {}: {}",
                    command.getClientId(), command.getClientOperationId(), ex.getMessage()
            );
            String message = ex instanceof NotFoundException ? NOT_FOUND_MESSAGE : INVALID_REQUEST_MESSAGE;
            return markTerminalFailure(command, SyncCommandStatus.REJECTED, message);
        } catch (Exception ex) {
            // Unexpected technical failure: FAILED is the only retriable state. Out of scope for
            // this batch — not a NotFoundException/IllegalArgumentException/BusinessException.
            return markTerminalFailure(
                    command,
                    SyncCommandStatus.FAILED,
                    ex.getMessage() != null ? ex.getMessage() : "Unexpected sync error"
            );
        }
    }

    private SyncCommandResponse markTerminalFailure(
            ClientSyncCommandEntity command,
            SyncCommandStatus status,
            String message
    ) {
        command.setStatus(status);
        command.setErrorMessage(message);
        command.setProcessedAt(LocalDateTime.now());
        command.setResultReferenceType(null);
        command.setResultReferenceId(null);

        // The processing transaction has rolled back, so this status write needs its own
        // transaction (REQUIRES_NEW).
        persistenceService.save(command);

        return new SyncCommandResponse(
                command.getClientOperationId(),
                status,
                null,
                null,
                message
        );
    }

    private SyncCommandResponse duplicateResponse(ClientSyncCommandEntity command) {
        return new SyncCommandResponse(
                command.getClientOperationId(),
                SyncCommandStatus.DUPLICATE,
                command.getResultReferenceType(),
                command.getResultReferenceId(),
                "Command already processed"
        );
    }

    private boolean isTerminal(SyncCommandStatus status) {
        return status == SyncCommandStatus.PROCESSED
                || status == SyncCommandStatus.REJECTED
                || status == SyncCommandStatus.DUPLICATE;
    }
}
