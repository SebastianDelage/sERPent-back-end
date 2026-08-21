package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.service.WarehouseAccessService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SupplierPaymentEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SupplierPaymentRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateSupplierPaymentRequest;
import com.empresa.serpent.transactions.web.dto.response.SupplierPaymentResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recording money paid against a supplier's balance.
 *
 * <p>THIS IS NOT AN EXPENSE. The purchase that created the debt already hit the result
 * when the goods came in; booking the payment as an expense would count the same money
 * twice. Nothing here writes an {@code ExpenseEntity}, so these amounts cannot reach the
 * expense listing.
 */
@Service
@RequiredArgsConstructor
public class SupplierPaymentService {

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierRepository supplierRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional
    public SupplierPaymentResponse create(CreateSupplierPaymentRequest request) {
        UserEntity createdBy = authenticatedUserService.requireCurrentUser();

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("El importe del pago tiene que ser mayor a cero.");
        }

        SupplierEntity supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() ->
                        new NotFoundException("Supplier not found: " + request.supplierId()));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new ValidationException(
                    "El proveedor \"" + supplier.getName() + "\" está inactivo.");
        }

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() ->
                        new NotFoundException("Payment method not found: " + request.paymentMethodId()));

        // Same resolution as a sale: the terminal decides when there is one, and the user
        // has to be assigned to the branch. Paying a supplier in cash empties a particular
        // drawer, so it is authorized like any other operation on that branch.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

        // Not capped at the outstanding balance, for the same reason as on the customer
        // side: paying more than owed leaves a credit in our favour, and that is real money.
        SupplierPaymentEntity payment = SupplierPaymentEntity.builder()
                .supplier(supplier)
                .warehouse(warehouse)
                .paymentMethod(paymentMethod)
                .amount(request.amount())
                .paymentDate(request.paymentDate())
                .note(normalizeOptional(request.note()))
                .createdByUserEntity(createdBy)
                .build();

        return toResponse(supplierPaymentRepository.save(payment));
    }

    /**
     * Payments in a date range, so the day's cash can be explained.
     *
     * <p>These amounts are not expenses and never appear in the expense listing, but the
     * cash physically left. Without this view the till count has an unexplained shortfall.
     */
    @Transactional(readOnly = true)
    public List<SupplierPaymentResponse> search(LocalDate dateFrom, LocalDate dateTo, Long paymentMethodId) {
        return supplierPaymentRepository.search(dateFrom, dateTo, paymentMethodId).stream()
                .map(this::toResponse)
                .toList();
    }

    private SupplierPaymentResponse toResponse(SupplierPaymentEntity payment) {
        UserEntity user = payment.getCreatedByUserEntity();
        WarehouseEntity warehouse = payment.getWarehouse();

        return new SupplierPaymentResponse(
                payment.getId(),
                payment.getSupplier().getId(),
                payment.getSupplier().getName(),
                payment.getPaymentMethod().getId(),
                payment.getPaymentMethod().getName(),
                warehouse == null ? null : warehouse.getId(),
                warehouse == null ? null : warehouse.getName(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getNote(),
                user == null ? null : fullName(user),
                payment.getCreatedAt()
        );
    }

    private String fullName(UserEntity user) {
        return user.getLastName() == null || user.getLastName().isBlank()
                ? user.getName()
                : user.getName() + " " + user.getLastName();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
