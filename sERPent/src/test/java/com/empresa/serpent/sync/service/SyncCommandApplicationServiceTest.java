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
import com.empresa.serpent.transactions.web.dto.request.CreateSaleItemRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.CreateSaleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncCommandApplicationServiceTest {

    @Mock
    private ClientSyncCommandRepository repository;

    @Mock
    private ClientSyncCommandPersistenceService persistenceService;

    @Mock
    private SaleApplicationService saleApplicationService;

    @Mock
    private ExpenseApplicationService expenseApplicationService;

    private ObjectMapper objectMapper;
    private SyncCommandApplicationService syncCommandApplicationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncCommandApplicationService = new SyncCommandApplicationService(
                repository,
                persistenceService,
                objectMapper,
                saleApplicationService,
                expenseApplicationService
        );
    }

    @Test
    @DisplayName("Should process CREATE_SALE command successfully")
    void shouldProcessCreateSaleCommandSuccessfully() throws Exception {
        CreateSaleRequest salePayload = new CreateSaleRequest(
                1L,
                "Consumidor Final",
                "12345678",
                "SYNC-0001",
                1L,
                1L,
                1L,
                "Sale synced from offline client",
                List.of(
                        new CreateSaleItemRequest(
                                1L,
                                "Pollo entero",
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        String payloadJson = objectMapper.writeValueAsString(salePayload);

        SyncCommandRequest request = new SyncCommandRequest(
                "device-01",
                "sale-0001",
                SyncCommandType.CREATE_SALE,
                payloadJson
        );

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-0001"))
                .thenReturn(Optional.empty());

        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenAnswer(invocation -> {
                    ClientSyncCommandEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(1L);
                    }
                    if (entity.getReceivedAt() == null) {
                        entity.setReceivedAt(LocalDateTime.now());
                    }
                    return entity;
                });

        when(saleApplicationService.createSale(any(CreateSaleRequest.class)))
                .thenReturn(new CreateSaleResponse(
                        4L,
                        2L,
                        "CONFIRMED",
                        "Sale created successfully"
                ));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response).isNotNull();
        assertThat(response.clientOperationId()).isEqualTo("sale-0001");
        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(response.resultReferenceType()).isEqualTo(SyncResultReferenceType.SALE);
        assertThat(response.resultReferenceId()).isEqualTo(2L);
        assertThat(response.message()).isEqualTo("Sale created successfully");

        verify(repository).findByClientIdAndClientOperationId("device-01", "sale-0001");
        verify(persistenceService, times(2)).save(any(ClientSyncCommandEntity.class));

        ArgumentCaptor<CreateSaleRequest> payloadCaptor = ArgumentCaptor.forClass(CreateSaleRequest.class);
        verify(saleApplicationService).createSale(payloadCaptor.capture());

        CreateSaleRequest capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload.customerName()).isEqualTo("Consumidor Final");
        assertThat(capturedPayload.invoiceNumber()).isEqualTo("SYNC-0001");
        assertThat(capturedPayload.items()).hasSize(1);
        assertThat(capturedPayload.items().get(0).productId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should process CREATE_EXPENSE command successfully")
    void shouldProcessCreateExpenseCommandSuccessfully() throws Exception {
        CreateExpenseRequest expensePayload = new CreateExpenseRequest(
                1L,
                1L,
                1L,
                1L,
                new BigDecimal("2500.0000"),
                "SYNC-EXP-001",
                "Expense synced from offline client",
                false,
                "Offline expense"
        );

        String payloadJson = objectMapper.writeValueAsString(expensePayload);

        SyncCommandRequest request = new SyncCommandRequest(
                "device-01",
                "expense-0001",
                SyncCommandType.CREATE_EXPENSE,
                payloadJson
        );

        when(repository.findByClientIdAndClientOperationId("device-01", "expense-0001"))
                .thenReturn(Optional.empty());

        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenAnswer(invocation -> {
                    ClientSyncCommandEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(2L);
                    }
                    if (entity.getReceivedAt() == null) {
                        entity.setReceivedAt(LocalDateTime.now());
                    }
                    return entity;
                });

        when(expenseApplicationService.createExpense(any(CreateExpenseRequest.class)))
                .thenReturn(new CreateExpenseResponse(
                        5L,
                        2L,
                        "CONFIRMED",
                        "Expense created successfully"
                ));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response).isNotNull();
        assertThat(response.clientOperationId()).isEqualTo("expense-0001");
        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(response.resultReferenceType()).isEqualTo(SyncResultReferenceType.EXPENSE);
        assertThat(response.resultReferenceId()).isEqualTo(2L);
        assertThat(response.message()).isEqualTo("Expense created successfully");

        verify(repository).findByClientIdAndClientOperationId("device-01", "expense-0001");
        verify(persistenceService, times(2)).save(any(ClientSyncCommandEntity.class));

        ArgumentCaptor<CreateExpenseRequest> payloadCaptor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseApplicationService).createExpense(payloadCaptor.capture());

        CreateExpenseRequest capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload.receiptNumber()).isEqualTo("SYNC-EXP-001");
        assertThat(capturedPayload.total()).isEqualByComparingTo("2500.0000");
    }

    @Test
    @DisplayName("Should return duplicate when command already exists")
    void shouldReturnDuplicateWhenCommandAlreadyExists() {
        ClientSyncCommandEntity existing = ClientSyncCommandEntity.builder()
                .id(10L)
                .clientId("device-01")
                .clientOperationId("sale-0001")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.PROCESSED)
                .payload("{}")
                .resultReferenceType(SyncResultReferenceType.SALE)
                .resultReferenceId(2L)
                .build();

        SyncCommandRequest request = new SyncCommandRequest(
                "device-01",
                "sale-0001",
                SyncCommandType.CREATE_SALE,
                "{}"
        );

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-0001"))
                .thenReturn(Optional.of(existing));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response).isNotNull();
        assertThat(response.clientOperationId()).isEqualTo("sale-0001");
        assertThat(response.status()).isEqualTo(SyncCommandStatus.DUPLICATE);
        assertThat(response.resultReferenceType()).isEqualTo(SyncResultReferenceType.SALE);
        assertThat(response.resultReferenceId()).isEqualTo(2L);
        assertThat(response.message()).isEqualTo("Command already processed");

        verify(repository).findByClientIdAndClientOperationId("device-01", "sale-0001");
        verifyNoInteractions(persistenceService);
        verifyNoInteractions(saleApplicationService);
        verifyNoInteractions(expenseApplicationService);
    }

    @Test
    @DisplayName("Should reject when payload is invalid")
    void shouldRejectWhenPayloadIsInvalid() {
        SyncCommandRequest request = new SyncCommandRequest(
                "device-01",
                "sale-bad-payload-1",
                SyncCommandType.CREATE_SALE,
                "{\"createdByUserId\":1,\"warehouseId\":1,\"items\":["
        );

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-bad-payload-1"))
                .thenReturn(Optional.empty());

        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenAnswer(invocation -> {
                    ClientSyncCommandEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(11L);
                    }
                    return entity;
                });

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response).isNotNull();
        assertThat(response.clientOperationId()).isEqualTo("sale-bad-payload-1");
        assertThat(response.status()).isEqualTo(SyncCommandStatus.REJECTED);
        assertThat(response.resultReferenceType()).isNull();
        assertThat(response.resultReferenceId()).isNull();
        assertThat(response.message()).isEqualTo("Invalid payload for CreateSaleRequest");

        verify(repository).findByClientIdAndClientOperationId("device-01", "sale-bad-payload-1");
        verify(persistenceService, times(2)).save(any(ClientSyncCommandEntity.class));
        verifyNoInteractions(saleApplicationService);
        verifyNoInteractions(expenseApplicationService);
    }

    @Test
    @DisplayName("Should reject when sale business validation fails")
    void shouldRejectWhenSaleBusinessValidationFails() throws Exception {
        CreateSaleRequest salePayload = new CreateSaleRequest(
                1L,
                "Consumidor Final",
                "12345678",
                "SYNC-0002",
                1L,
                1L,
                1L,
                "Sale synced from offline client",
                List.of(
                        new CreateSaleItemRequest(
                                1L,
                                "Pollo entero",
                                new BigDecimal("9999.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        String payloadJson = objectMapper.writeValueAsString(salePayload);

        SyncCommandRequest request = new SyncCommandRequest(
                "device-01",
                "sale-stock-error-1",
                SyncCommandType.CREATE_SALE,
                payloadJson
        );

        when(repository.findByClientIdAndClientOperationId("device-01", "sale-stock-error-1"))
                .thenReturn(Optional.empty());

        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenAnswer(invocation -> {
                    ClientSyncCommandEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(12L);
                    }
                    return entity;
                });

        when(saleApplicationService.createSale(any(CreateSaleRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "Insufficient stock for product 1 in warehouse 1. Current stock: 29.000, requested: 9999"
                ));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response).isNotNull();
        assertThat(response.clientOperationId()).isEqualTo("sale-stock-error-1");
        assertThat(response.status()).isEqualTo(SyncCommandStatus.REJECTED);
        assertThat(response.resultReferenceType()).isNull();
        assertThat(response.resultReferenceId()).isNull();
        assertThat(response.message())
                .isEqualTo("Insufficient stock for product 1 in warehouse 1. Current stock: 29.000, requested: 9999");

        verify(repository).findByClientIdAndClientOperationId("device-01", "sale-stock-error-1");
        verify(persistenceService, times(2)).save(any(ClientSyncCommandEntity.class));
        verify(saleApplicationService).createSale(any(CreateSaleRequest.class));
        verifyNoInteractions(expenseApplicationService);
    }

    @Test
    @DisplayName("Should reject when expense business validation fails")
    void shouldRejectWhenExpenseBusinessValidationFails() throws Exception {
        CreateExpenseRequest expensePayload = new CreateExpenseRequest(
                1L,
                1L,
                1L,
                999L,
                new BigDecimal("2500.0000"),
                "SYNC-EXP-002",
                "Expense synced from offline client",
                false,
                "Offline expense"
        );

        String payloadJson = objectMapper.writeValueAsString(expensePayload);

        SyncCommandRequest request = new SyncCommandRequest(
                "device-01",
                "expense-category-error-1",
                SyncCommandType.CREATE_EXPENSE,
                payloadJson
        );

        when(repository.findByClientIdAndClientOperationId("device-01", "expense-category-error-1"))
                .thenReturn(Optional.empty());

        when(persistenceService.save(any(ClientSyncCommandEntity.class)))
                .thenAnswer(invocation -> {
                    ClientSyncCommandEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(13L);
                    }
                    return entity;
                });

        when(expenseApplicationService.createExpense(any(CreateExpenseRequest.class)))
                .thenThrow(new NotFoundException("Expense category not found: 999"));

        SyncCommandResponse response = syncCommandApplicationService.process(request);

        assertThat(response).isNotNull();
        assertThat(response.clientOperationId()).isEqualTo("expense-category-error-1");
        assertThat(response.status()).isEqualTo(SyncCommandStatus.REJECTED);
        assertThat(response.resultReferenceType()).isNull();
        assertThat(response.resultReferenceId()).isNull();
        assertThat(response.message()).isEqualTo("Expense category not found: 999");

        verify(repository).findByClientIdAndClientOperationId("device-01", "expense-category-error-1");
        verify(persistenceService, times(2)).save(any(ClientSyncCommandEntity.class));
        verify(expenseApplicationService).createExpense(any(CreateExpenseRequest.class));
        verifyNoInteractions(saleApplicationService);
    }
}