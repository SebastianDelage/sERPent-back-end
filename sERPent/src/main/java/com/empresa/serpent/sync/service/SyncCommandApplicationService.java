package com.empresa.serpent.sync.service;

import com.empresa.serpent.shared.exception.BusinessException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import com.empresa.serpent.sync.web.dto.request.SyncCommandRequest;
import com.empresa.serpent.sync.web.dto.response.SyncCommandResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SyncCommandApplicationService {

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
        } catch (BusinessException | NotFoundException | IllegalArgumentException ex) {
            // Expected business rejection (insufficient stock, validation, not found, bad payload):
            // terminal REJECTED, not a retriable failure.
            return markTerminalFailure(command, SyncCommandStatus.REJECTED, ex.getMessage());
        } catch (Exception ex) {
            // Unexpected technical failure: FAILED is the only retriable state.
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
