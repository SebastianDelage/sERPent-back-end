package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.ExpenseCategoryRepository;
import com.empresa.serpent.transactions.repository.ExpenseRepository;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseApplicationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @InjectMocks
    private ExpenseApplicationService expenseApplicationService;

    @Test
    @DisplayName("Should create expense successfully")
    void shouldCreateExpenseSuccessfully() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                1L,
                1L,
                1L,
                1L,
                new BigDecimal("3000.0000"),
                "REC-002",
                "Compra de insumos",
                false,
                "Notas de prueba"
        );

        UserEntity user = UserEntity.builder().id(1L).name("Admin").build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).name("Cash").active(true).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).name("Proveedor Central").active(true).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).name("Insumos").active(true).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(expenseRepository.findByReceiptNumberIgnoreCase("REC-002")).thenReturn(Optional.empty());

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        when(expenseRepository.save(any(ExpenseEntity.class))).thenAnswer(invocation -> {
            ExpenseEntity expense = invocation.getArgument(0);
            expense.setId(20L);
            return expense;
        });

        CreateExpenseResponse response = expenseApplicationService.createExpense(request);

        assertNotNull(response);
        assertEquals(10L, response.transactionId());
        assertEquals(20L, response.expenseId());
        assertEquals("CONFIRMED", response.status());
        assertEquals("Expense created successfully", response.message());

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertEquals(TransactionType.EXPENSE, savedTransaction.getType());
        assertEquals(TransactionStatus.CONFIRMED, savedTransaction.getStatus());
        assertEquals(0, savedTransaction.getTotal().compareTo(new BigDecimal("3000.0000")));
        assertEquals("Compra de insumos", savedTransaction.getDescription());
        assertEquals(paymentMethod, savedTransaction.getPaymentMethod());
        assertEquals(user, savedTransaction.getCreatedByUserEntity());

        ArgumentCaptor<ExpenseEntity> expenseCaptor = ArgumentCaptor.forClass(ExpenseEntity.class);
        verify(expenseRepository).save(expenseCaptor.capture());

        ExpenseEntity savedExpense = expenseCaptor.getValue();
        assertEquals(supplier, savedExpense.getSupplier());
        assertEquals(category, savedExpense.getExpenseCategory());
        assertEquals("REC-002", savedExpense.getReceiptNumber());
        assertFalse(savedExpense.getReimbursable());
        assertEquals("Notas de prueba", savedExpense.getNotes());
    }

    @Test
    @DisplayName("Should create expense without payment method")
    void shouldCreateExpenseWithoutPaymentMethod() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                1L,
                null,
                1L,
                1L,
                new BigDecimal("1500.0000"),
                "REC-003",
                "Gasto sin método de pago",
                true,
                null
        );

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).active(true).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(expenseRepository.findByReceiptNumberIgnoreCase("REC-003")).thenReturn(Optional.empty());

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(11L);
            return tx;
        });

        when(expenseRepository.save(any(ExpenseEntity.class))).thenAnswer(invocation -> {
            ExpenseEntity expense = invocation.getArgument(0);
            expense.setId(21L);
            return expense;
        });

        CreateExpenseResponse response = expenseApplicationService.createExpense(request);

        assertEquals(11L, response.transactionId());
        assertEquals(21L, response.expenseId());

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertNull(transactionCaptor.getValue().getPaymentMethod());
    }

    @Test
    @DisplayName("Should allow null receipt number")
    void shouldAllowNullReceiptNumber() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                1L,
                null,
                null,
                1L,
                new BigDecimal("500.0000"),
                null,
                "Gasto sin comprobante",
                null,
                null
        );

        UserEntity user = UserEntity.builder().id(1L).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).active(true).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(12L);
            return tx;
        });

        when(expenseRepository.save(any(ExpenseEntity.class))).thenAnswer(invocation -> {
            ExpenseEntity expense = invocation.getArgument(0);
            expense.setId(22L);
            return expense;
        });

        CreateExpenseResponse response = expenseApplicationService.createExpense(request);

        assertEquals(12L, response.transactionId());
        assertEquals(22L, response.expenseId());
        verify(expenseRepository, never()).findByReceiptNumberIgnoreCase(any());
    }

    @Test
    @DisplayName("Should throw when there is no authenticated user")
    void shouldThrowWhenUserDoesNotExist() {
        CreateExpenseRequest request = baseRequest();

        when(authenticatedUserService.requireCurrentUser())
                .thenThrow(new ForbiddenException("Tenés que iniciar sesión para realizar esta acción."));

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Tenés que iniciar sesión para realizar esta acción.", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when payment method does not exist")
    void shouldThrowWhenPaymentMethodDoesNotExist() {
        CreateExpenseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Payment method not found: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when supplier does not exist")
    void shouldThrowWhenSupplierDoesNotExist() {
        CreateExpenseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Supplier not found: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when supplier is inactive")
    void shouldThrowWhenSupplierIsInactive() {
        CreateExpenseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(false).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Supplier is inactive: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when expense category does not exist")
    void shouldThrowWhenExpenseCategoryDoesNotExist() {
        CreateExpenseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Expense category not found: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when expense category is inactive")
    void shouldThrowWhenExpenseCategoryIsInactive() {
        CreateExpenseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).active(false).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Expense category is inactive: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when total is null")
    void shouldThrowWhenTotalIsNull() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                1L,
                null,
                null,
                1L,
                null,
                "REC-010",
                "Gasto inválido",
                false,
                null
        );

        UserEntity user = UserEntity.builder().id(1L).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).active(true).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Total cannot be null", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when total is negative")
    void shouldThrowWhenTotalIsNegative() {
        CreateExpenseRequest request = new CreateExpenseRequest(
                1L,
                null,
                null,
                1L,
                new BigDecimal("-1.0000"),
                "REC-011",
                "Gasto inválido",
                false,
                null
        );

        UserEntity user = UserEntity.builder().id(1L).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).active(true).build();
        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Total cannot be negative", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when receipt number already exists")
    void shouldThrowWhenReceiptNumberAlreadyExists() {
        CreateExpenseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        ExpenseCategoryEntity category = ExpenseCategoryEntity.builder().id(1L).active(true).build();
        ExpenseEntity existingExpense = ExpenseEntity.builder().id(99L).build();

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(expenseRepository.findByReceiptNumberIgnoreCase("REC-002")).thenReturn(Optional.of(existingExpense));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> expenseApplicationService.createExpense(request)
        );

        assertEquals("Receipt number already exists: REC-002", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    private CreateExpenseRequest baseRequest() {
        return new CreateExpenseRequest(
                1L,
                1L,
                1L,
                1L,
                new BigDecimal("3000.0000"),
                "REC-002",
                "Compra de insumos",
                false,
                "Notas"
        );
    }
}