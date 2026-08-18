package com.empresa.serpent.sync.service;

import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncCommandResultServiceTest {

    @Mock
    private ClientSyncCommandRepository repository;

    @Mock
    private SaleApplicationService saleApplicationService;

    @Mock
    private ExpenseApplicationService expenseApplicationService;

    private ObjectMapper objectMapper;
    private SyncCommandResultService resultService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        resultService = new SyncCommandResultService(
                repository, objectMapper, saleApplicationService, expenseApplicationService);
    }

    @Test
    @DisplayName("Creates the sale and marks the command PROCESSED with the SALE reference")
    void processCreateSale_marksProcessedWithReference() throws Exception {
        CreateSaleRequest salePayload = new CreateSaleRequest(
                null, "Consumidor Final", null, "INV-1", null, 1L, 1L, "Offline sale",
                List.of(new CreateSaleItemRequest(1L, "Pollo entero",
                        new BigDecimal("1.000"), new BigDecimal("4500.0000"))));

        ClientSyncCommandEntity command = ClientSyncCommandEntity.builder()
                .id(1L).clientId("device-01").clientOperationId("sale-1")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.RECEIVED)
                .payload(objectMapper.writeValueAsString(salePayload))
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(command));
        when(saleApplicationService.createSaleFromSync(any(CreateSaleRequest.class)))
                .thenReturn(new CreateSaleResponse(4L, 2L, "CONFIRMED", "Sale created successfully"));

        SyncCommandResponse response = resultService.processCreateSale(1L);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(response.resultReferenceType()).isEqualTo(SyncResultReferenceType.SALE);
        assertThat(response.resultReferenceId()).isEqualTo(2L);

        ArgumentCaptor<ClientSyncCommandEntity> captor = ArgumentCaptor.forClass(ClientSyncCommandEntity.class);
        verify(repository).save(captor.capture());
        ClientSyncCommandEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(saved.getResultReferenceType()).isEqualTo(SyncResultReferenceType.SALE);
        assertThat(saved.getResultReferenceId()).isEqualTo(2L);
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Creates the expense and marks the command PROCESSED with the EXPENSE reference")
    void processCreateExpense_marksProcessedWithReference() throws Exception {
        CreateExpenseRequest expensePayload = new CreateExpenseRequest(
                1L, null, null, 1L, new BigDecimal("2500.0000"), "EXP-1", "Offline expense", false, null);

        ClientSyncCommandEntity command = ClientSyncCommandEntity.builder()
                .id(2L).clientId("device-01").clientOperationId("expense-1")
                .commandType(SyncCommandType.CREATE_EXPENSE)
                .status(SyncCommandStatus.RECEIVED)
                .payload(objectMapper.writeValueAsString(expensePayload))
                .build();

        when(repository.findById(2L)).thenReturn(Optional.of(command));
        when(expenseApplicationService.createExpenseFromSync(any(CreateExpenseRequest.class)))
                .thenReturn(new CreateExpenseResponse(5L, 3L, "CONFIRMED", "Expense created successfully"));

        SyncCommandResponse response = resultService.processCreateExpense(2L);

        assertThat(response.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(response.resultReferenceType()).isEqualTo(SyncResultReferenceType.EXPENSE);
        assertThat(response.resultReferenceId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("An invalid payload raises IllegalArgumentException and never creates a sale")
    void processCreateSale_invalidPayloadThrows() {
        ClientSyncCommandEntity command = ClientSyncCommandEntity.builder()
                .id(3L).clientId("device-01").clientOperationId("sale-bad")
                .commandType(SyncCommandType.CREATE_SALE)
                .status(SyncCommandStatus.RECEIVED)
                .payload("{not-json")
                .build();

        when(repository.findById(3L)).thenReturn(Optional.of(command));

        assertThatThrownBy(() -> resultService.processCreateSale(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid payload for CreateSaleRequest");

        verifyNoInteractions(saleApplicationService);
        verify(repository, never()).save(any());
    }
}
