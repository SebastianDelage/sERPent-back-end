package com.empresa.serpent.sync.service;

import com.empresa.serpent.shared.exception.InsufficientStockException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import com.empresa.serpent.sync.web.dto.request.SyncCommandRequest;
import com.empresa.serpent.sync.web.dto.response.SyncCommandResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncCommandApplicationServiceTest {

    @Mock
    private ClientSyncCommandRepository repository;

    @Mock
    private ClientSyncCommandPersistenceService persistenceService;

    @Mock
    private SyncCommandResultService resultService;

    private SyncCommandApplicationService syncCommandApplicationService;

    @BeforeEach
    void setUp() {
        syncCommandApplicationService = new SyncCommandApplicationService(
                repository,
                persistenceService,
                resultService
        );
    }

    private SyncCommandRequest saleRequest(String operationId) {
        return new SyncCommandRequest("device-01", operationId, SyncCommandType.CREATE_SALE, "{}");
    }

    private void stubReceivedInsert(long id) {
        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenAnswer(invocation -> {
                    ClientSyncCommandEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(id);
                    }
                    return entity;
                });
    }

    @Test
    @DisplayName("Processes a new CREATE_SALE and persists RECEIVED once (PROCESSED is written by the result service)")
    void shouldProcessNewCreateSale() {
        SyncCommandRequest request = saleRequest("sale-0001");

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-0001"))
                .thenReturn(Optional.empty());
        stubReceivedInsert(1L);
        when(resultService.processCreateSale(1L))
                .thenReturn(new SyncCommandResponse(
                        "sale-0001", SyncCommandStatus.PROCESSED,
                        SyncResultReferenceType.SALE, 2L, "Sale created successfully"));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(response.resultReferenceType()).isEqualTo(SyncResultReferenceType.SALE);
        assertThat(response.resultReferenceId()).isEqualTo(2L);

        // Defect 2 made visible: RECEIVED insert is the ONLY persistenceService.save on success.
        verify(persistenceService, times(1)).save(any(ClientSyncCommandEntity.class));
        verify(resultService).processCreateSale(1L);
    }

    @Test
    @DisplayName("Returns DUPLICATE for a terminal (PROCESSED) command without reprocessing")
    void shouldReturnDuplicateWhenExistingIsTerminal() {
        ClientSyncCommandEntity existing = ClientSyncCommandEntity.builder()
                .id(10L).clientId("device-01").clientOperationId("sale-0001")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.PROCESSED)
                .resultReferenceType(SyncResultReferenceType.SALE).resultReferenceId(2L)
                .payload("{}").build();

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-0001"))
                .thenReturn(Optional.of(existing));

        SyncCommandResponse response = syncCommandApplicationService.process(saleRequest("sale-0001"));

        assertThat(response.status()).isEqualTo(SyncCommandStatus.DUPLICATE);
        assertThat(response.resultReferenceId()).isEqualTo(2L);
        verifyNoInteractions(persistenceService);
        verifyNoInteractions(resultService);
    }

    @Test
    @DisplayName("Reprocesses (does not duplicate) a command left in RECEIVED, reusing the same row")
    void shouldReprocessWhenExistingIsReceived() {
        ClientSyncCommandEntity orphan = ClientSyncCommandEntity.builder()
                .id(15L).clientId("device-01").clientOperationId("sale-orphan")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.RECEIVED)
                .payload("{}").build();

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-orphan"))
                .thenReturn(Optional.of(orphan));
        when(resultService.processCreateSale(15L))
                .thenReturn(new SyncCommandResponse(
                        "sale-orphan", SyncCommandStatus.PROCESSED,
                        SyncResultReferenceType.SALE, 3L, "Sale created successfully"));

        SyncCommandResponse response = syncCommandApplicationService.process(saleRequest("sale-orphan"));

        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        // Reuses the existing row: no new RECEIVED insert.
        verify(persistenceService, never()).save(any(ClientSyncCommandEntity.class));
        verify(resultService).processCreateSale(15L);
    }

    @Test
    @DisplayName("Reprocesses a FAILED command (FAILED is the only retriable state)")
    void shouldReprocessWhenExistingIsFailed() {
        ClientSyncCommandEntity failed = ClientSyncCommandEntity.builder()
                .id(16L).clientId("device-01").clientOperationId("sale-failed")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.FAILED)
                .payload("{}").build();

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-failed"))
                .thenReturn(Optional.of(failed));
        when(resultService.processCreateSale(16L))
                .thenReturn(new SyncCommandResponse(
                        "sale-failed", SyncCommandStatus.PROCESSED,
                        SyncResultReferenceType.SALE, 4L, "Sale created successfully"));

        SyncCommandResponse response = syncCommandApplicationService.process(saleRequest("sale-failed"));

        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        verify(resultService).processCreateSale(16L);
    }

    @Test
    @DisplayName("Defect 1: a BusinessException (insufficient stock) is REJECTED, not FAILED")
    void shouldRejectWhenResultServiceThrowsBusinessException() {
        SyncCommandRequest request = saleRequest("sale-stock-error");

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-stock-error"))
                .thenReturn(Optional.empty());
        stubReceivedInsert(20L);
        when(resultService.processCreateSale(20L))
                .thenThrow(new InsufficientStockException(
                        "No hay stock suficiente de \"Pollo entero\" en el depósito seleccionado para completar la operación."));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.REJECTED);
        assertThat(response.message()).contains("No hay stock suficiente");
        assertThat(response.resultReferenceId()).isNull();

        // The command is persisted terminally as REJECTED (RECEIVED insert + REJECTED marking).
        ArgumentCaptor<ClientSyncCommandEntity> captor = ArgumentCaptor.forClass(ClientSyncCommandEntity.class);
        verify(persistenceService, times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SyncCommandStatus.REJECTED);
    }

    @Test
    @DisplayName("Defect 1: a NotFoundException is REJECTED, with a sanitized message (no raw id/English leak)")
    void shouldRejectWhenResultServiceThrowsNotFound() {
        SyncCommandRequest request =
                new SyncCommandRequest("device-01", "expense-missing-cat", SyncCommandType.CREATE_EXPENSE, "{}");

        when(repository.findByClientIdAndClientOperationId("device-01", "expense-missing-cat"))
                .thenReturn(Optional.empty());
        stubReceivedInsert(21L);
        when(resultService.processCreateExpense(21L))
                .thenThrow(new NotFoundException("Expense category not found: 999"));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.REJECTED);
        // SyncCommandResponse.message bypasses GlobalExceptionHandler, so the sync flow sanitizes
        // it itself: the raw "Expense category not found: 999" only reaches the log now.
        assertThat(response.message()).isEqualTo("No se encontró el recurso solicitado.");
    }

    @Test
    @DisplayName("An IllegalArgumentException is REJECTED, with a sanitized message (no raw id/English leak)")
    void shouldRejectWhenResultServiceThrowsIllegalArgument() {
        SyncCommandRequest request = saleRequest("sale-inactive-warehouse");

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-inactive-warehouse"))
                .thenReturn(Optional.empty());
        stubReceivedInsert(23L);
        when(resultService.processCreateSale(23L))
                .thenThrow(new IllegalArgumentException("Source warehouse is inactive: 42"));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.REJECTED);
        assertThat(response.message()).isEqualTo("La solicitud no es válida.");
    }

    @Test
    @DisplayName("An unexpected technical exception is FAILED (the only retriable state)")
    void shouldFailWhenResultServiceThrowsUnexpected() {
        SyncCommandRequest request = saleRequest("sale-boom");

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-boom"))
                .thenReturn(Optional.empty());
        stubReceivedInsert(22L);
        when(resultService.processCreateSale(22L))
                .thenThrow(new RuntimeException("connection reset"));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.FAILED);
        assertThat(response.message()).isEqualTo("connection reset");

        ArgumentCaptor<ClientSyncCommandEntity> captor = ArgumentCaptor.forClass(ClientSyncCommandEntity.class);
        verify(persistenceService, times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SyncCommandStatus.FAILED);
    }

    @Test
    @DisplayName("A concurrent-insert constraint collision is resolved as DUPLICATE, not FAILED")
    void shouldReturnDuplicateOnConcurrentInsertCollision() {
        SyncCommandRequest request = saleRequest("sale-race");

        ClientSyncCommandEntity concurrent = ClientSyncCommandEntity.builder()
                .id(30L).clientId("device-01").clientOperationId("sale-race")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.PROCESSED)
                .resultReferenceType(SyncResultReferenceType.SALE).resultReferenceId(7L)
                .payload("{}").build();

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-race"))
                .thenReturn(Optional.empty())      // first lookup: not there yet
                .thenReturn(Optional.of(concurrent)); // after collision: the winner's row
        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.DUPLICATE);
        assertThat(response.resultReferenceId()).isEqualTo(7L);
        verifyNoInteractions(resultService);
    }
}
