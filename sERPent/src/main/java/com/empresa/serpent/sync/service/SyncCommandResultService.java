package com.empresa.serpent.sync.service;

import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Runs a sync command's business operation and marks the command PROCESSED within ONE physical
 * transaction.
 *
 * <p>Both {@code processCreateSale} and {@code processCreateExpense} are {@code @Transactional}
 * (REQUIRED) and are invoked as a separate bean from {@code SyncCommandApplicationService}, so the
 * proxy actually starts a transaction. The delegated {@code createSale}/{@code createExpense} are
 * themselves {@code @Transactional} REQUIRED and therefore JOIN this transaction — no nested
 * commit — and the PROCESSED marking is written here through the repository directly, never through
 * the REQUIRES_NEW {@code ClientSyncCommandPersistenceService}. As a result the sale/expense rows
 * and the command's PROCESSED state commit together or roll back together. A crash between the two
 * can never leave a committed sale/expense with the command stuck in RECEIVED: RECEIVED
 * unambiguously means "the operation was not created", so a retry can safely reprocess.
 */
@Service
@RequiredArgsConstructor
public class SyncCommandResultService {

    private final ClientSyncCommandRepository repository;
    private final ObjectMapper objectMapper;
    private final SaleApplicationService saleApplicationService;
    private final ExpenseApplicationService expenseApplicationService;

    @Transactional
    public SyncCommandResponse processCreateSale(Long commandId) {
        ClientSyncCommandEntity command = loadCommand(commandId);

        CreateSaleRequest payload = readPayload(command.getPayload(), CreateSaleRequest.class);
        CreateSaleResponse result = saleApplicationService.createSaleFromSync(payload);

        markProcessed(command, SyncResultReferenceType.SALE, result.saleId());

        return new SyncCommandResponse(
                command.getClientOperationId(),
                SyncCommandStatus.PROCESSED,
                SyncResultReferenceType.SALE,
                result.saleId(),
                result.message()
        );
    }

    @Transactional
    public SyncCommandResponse processCreateExpense(Long commandId) {
        ClientSyncCommandEntity command = loadCommand(commandId);

        CreateExpenseRequest payload = readPayload(command.getPayload(), CreateExpenseRequest.class);
        CreateExpenseResponse result = expenseApplicationService.createExpenseFromSync(payload);

        markProcessed(command, SyncResultReferenceType.EXPENSE, result.expenseId());

        return new SyncCommandResponse(
                command.getClientOperationId(),
                SyncCommandStatus.PROCESSED,
                SyncResultReferenceType.EXPENSE,
                result.expenseId(),
                result.message()
        );
    }

    private void markProcessed(
            ClientSyncCommandEntity command,
            SyncResultReferenceType referenceType,
            Long referenceId
    ) {
        command.setStatus(SyncCommandStatus.PROCESSED);
        command.setProcessedAt(LocalDateTime.now());
        command.setResultReferenceType(referenceType);
        command.setResultReferenceId(referenceId);
        command.setErrorMessage(null);
        repository.save(command);
    }

    private ClientSyncCommandEntity loadCommand(Long commandId) {
        return repository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Sync command not found: " + commandId));
    }

    private <T> T readPayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid payload for " + clazz.getSimpleName());
        }
    }
}
