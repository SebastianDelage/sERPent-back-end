package com.empresa.serpent.sync.service;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import com.empresa.serpent.sync.web.dto.request.SyncCommandRequest;
import com.empresa.serpent.sync.web.dto.response.SyncCommandResponse;
import com.empresa.serpent.transactions.service.ExpenseApplicationService;
import com.empresa.serpent.transactions.service.SaleApplicationService;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.CreateSaleResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SyncCommandApplicationService {

    private final ClientSyncCommandRepository repository;
    private final ClientSyncCommandPersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final SaleApplicationService saleApplicationService;
    private final ExpenseApplicationService expenseApplicationService;

    public SyncCommandResponse process(SyncCommandRequest request) {

        Optional<ClientSyncCommandEntity> existing =
                repository.findByClientIdAndClientOperationId(
                        request.clientId(),
                        request.clientOperationId()
                );

        if (existing.isPresent()) {
            ClientSyncCommandEntity cmd = existing.get();

            return new SyncCommandResponse(
                    cmd.getClientOperationId(),
                    SyncCommandStatus.DUPLICATE,
                    cmd.getResultReferenceType(),
                    cmd.getResultReferenceId(),
                    "Command already processed"
            );
        }

        ClientSyncCommandEntity command = ClientSyncCommandEntity.builder()
                .clientId(request.clientId())
                .clientOperationId(request.clientOperationId())
                .commandType(request.commandType())
                .status(SyncCommandStatus.RECEIVED)
                .payload(request.payload())
                .build();

        command = persistenceService.save(command);

        try {
            return switch (request.commandType()) {
                case CREATE_SALE -> processCreateSale(command);
                case CREATE_EXPENSE -> processCreateExpense(command);
            };
        } catch (IllegalArgumentException | NotFoundException ex) {
            command.setStatus(SyncCommandStatus.REJECTED);
            command.setErrorMessage(ex.getMessage());
            command.setProcessedAt(LocalDateTime.now());
            persistenceService.save(command);

            return new SyncCommandResponse(
                    command.getClientOperationId(),
                    SyncCommandStatus.REJECTED,
                    null,
                    null,
                    ex.getMessage()
            );
        } catch (Exception ex) {
            command.setStatus(SyncCommandStatus.FAILED);
            command.setErrorMessage(ex.getMessage());
            command.setProcessedAt(LocalDateTime.now());
            persistenceService.save(command);

            return new SyncCommandResponse(
                    command.getClientOperationId(),
                    SyncCommandStatus.FAILED,
                    null,
                    null,
                    ex.getMessage() != null ? ex.getMessage() : "Unexpected sync error"
            );
        }
    }

    private SyncCommandResponse processCreateSale(ClientSyncCommandEntity command) {
        CreateSaleRequest payload = readPayload(command.getPayload(), CreateSaleRequest.class);

        CreateSaleResponse result = saleApplicationService.createSale(payload);

        command.setStatus(SyncCommandStatus.PROCESSED);
        command.setProcessedAt(LocalDateTime.now());
        command.setResultReferenceType(SyncResultReferenceType.SALE);
        command.setResultReferenceId(result.saleId());
        persistenceService.save(command);

        return new SyncCommandResponse(
                command.getClientOperationId(),
                SyncCommandStatus.PROCESSED,
                SyncResultReferenceType.SALE,
                result.saleId(),
                result.message()
        );
    }

    private SyncCommandResponse processCreateExpense(ClientSyncCommandEntity command) {
        CreateExpenseRequest payload = readPayload(command.getPayload(), CreateExpenseRequest.class);

        CreateExpenseResponse result = expenseApplicationService.createExpense(payload);

        command.setStatus(SyncCommandStatus.PROCESSED);
        command.setProcessedAt(LocalDateTime.now());
        command.setResultReferenceType(SyncResultReferenceType.EXPENSE);
        command.setResultReferenceId(result.expenseId());
        persistenceService.save(command);

        return new SyncCommandResponse(
                command.getClientOperationId(),
                SyncCommandStatus.PROCESSED,
                SyncResultReferenceType.EXPENSE,
                result.expenseId(),
                result.message()
        );
    }

    private <T> T readPayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid payload for " + clazz.getSimpleName());
        }
    }
}