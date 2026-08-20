package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ExpenseApplicationService {

    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuthenticatedUserService authenticatedUserService;

    /** Online path: the acting user is whoever is holding the session. */
    @Transactional
    public CreateExpenseResponse createExpense(CreateExpenseRequest request) {
        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        return createExpense(request, createdBy);
    }

    /**
     * Offline sync path: the acting user is the one named in the queued payload, not whoever
     * happens to be uploading it — attribution belongs to whoever recorded the expense.
     *
     * <p>Knowingly the weaker of the two paths: the payload is client-supplied and therefore
     * forgeable. Accepted because the alternative — attributing the expense to whoever syncs —
     * corrupts the data itself, which is worse than the residual risk.
     */
    @Transactional
    public CreateExpenseResponse createExpenseFromSync(CreateExpenseRequest request) {
        if (request.createdByUserId() == null) {
            throw new ValidationException("La operación no indica el usuario que la registró.");
        }

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        return createExpense(request, createdBy);
    }

    private CreateExpenseResponse createExpense(CreateExpenseRequest request, UserEntity createdBy) {

        PaymentMethodEntity paymentMethod = null;
        if (request.paymentMethodId() != null) {
            paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                    .orElseThrow(() ->
                            new NotFoundException("Payment method not found: " + request.paymentMethodId()));
        }

        SupplierEntity supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() ->
                            new NotFoundException("Supplier not found: " + request.supplierId()));

            if (!Boolean.TRUE.equals(supplier.getActive())) {
                throw new IllegalArgumentException("Supplier is inactive: " + request.supplierId());
            }
        }

        WarehouseEntity warehouse = resolveWarehouse(request.warehouseId());

        ExpenseCategoryEntity expenseCategory = expenseCategoryRepository.findById(request.expenseCategoryId())
                .orElseThrow(() ->
                        new NotFoundException("Expense category not found: " + request.expenseCategoryId()));

        if (!Boolean.TRUE.equals(expenseCategory.getActive())) {
            throw new IllegalArgumentException("Expense category is inactive: " + request.expenseCategoryId());
        }

        validateTotal(request.total());
        validateReceiptNumber(request.receiptNumber());

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.CONFIRMED)
                .description(normalizeOptional(request.description()))
                .paymentMethod(paymentMethod)
                .createdByUserEntity(createdBy)
                .total(request.total())
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        ExpenseEntity expense = ExpenseEntity.builder()
                .transaction(savedTransaction)
                .supplier(supplier)
                .warehouse(warehouse)
                .expenseCategory(expenseCategory)
                .receiptNumber(normalizeOptional(request.receiptNumber()))
                .reimbursable(request.reimbursable() != null ? request.reimbursable() : false)
                .notes(normalizeOptional(request.notes()))
                .build();

        ExpenseEntity savedExpense = expenseRepository.save(expense);

        return new CreateExpenseResponse(
                savedTransaction.getId(),
                savedExpense.getId(),
                savedTransaction.getStatus().name(),
                "Expense created successfully"
        );
    }

    /**
     * The branch this expense belongs to, or null for a general one.
     *
     * <p>DELIBERATELY NOT {@code WarehouseAccessService.resolveForOperation}, and the two
     * differences are both on purpose:
     *
     * <p>1. No assignment check. Every stock operation requires the warehouse to be one of
     * the acting user's, because it says where the goods physically moved. An expense moves
     * neither stock nor cash — it records who a cost belongs to — and in most shops one
     * person loads every expense from one place. Requiring assignment would stop them from
     * booking the other branch's rent. Worth revisiting once there are roles: the right gate
     * for this is a role, not a warehouse assignment.
     *
     * <p>2. Inactive warehouses are ACCEPTED. Stock operations reject them correctly, since
     * nothing can move through a closed branch. But expenses arrive after the fact: close a
     * branch in March and its final electricity bill shows up in April. Rejecting it would
     * force that bill to be recorded as general, which is exactly the false attribution this
     * whole column exists to avoid. The UI marks inactive warehouses so nobody picks one by
     * mistake.
     */
    private WarehouseEntity resolveWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }

        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));
    }

    private void validateTotal(BigDecimal total) {
        if (total == null) {
            throw new IllegalArgumentException("Total cannot be null");
        }

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total cannot be negative");
        }
    }

    private void validateReceiptNumber(String receiptNumber) {
        String normalized = normalizeOptional(receiptNumber);

        if (normalized == null) {
            return;
        }

        expenseRepository.findByReceiptNumberIgnoreCase(normalized)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Receipt number already exists: " + normalized);
                });
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}