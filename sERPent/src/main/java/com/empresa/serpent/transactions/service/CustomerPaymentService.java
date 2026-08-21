package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.repository.CustomerRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.service.WarehouseAccessService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.transactions.domain.entity.CustomerPaymentEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.repository.CustomerPaymentRepository;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateCustomerPaymentRequest;
import com.empresa.serpent.transactions.web.dto.response.CustomerPaymentResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recording money collected against a customer's balance.
 *
 * <p>THIS IS NOT A SALE. The sale that created the debt already counted as revenue when it
 * happened; counting the collection again would report the same money twice. Nothing here
 * writes a {@code TransactionEntity}, which is what keeps these amounts out of every sales
 * aggregation — they all filter on {@code transactions.type}.
 */
@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerPaymentRepository customerPaymentRepository;
    private final CustomerRepository customerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional
    public CustomerPaymentResponse create(CreateCustomerPaymentRequest request) {
        UserEntity createdBy = authenticatedUserService.requireCurrentUser();

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("El importe del cobro tiene que ser mayor a cero.");
        }

        CustomerEntity customer = customerRepository.findById(request.customerId())
                .orElseThrow(() ->
                        new NotFoundException("Customer not found: " + request.customerId()));

        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw new ValidationException(
                    "El cliente \"" + customer.getName() + "\" está inactivo.");
        }

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() ->
                        new NotFoundException("Payment method not found: " + request.paymentMethodId()));

        // Same resolution as a sale: the terminal decides when there is one, and the user
        // has to be assigned to the branch. Collecting cash is money entering a particular
        // drawer, so it is authorized like any other operation on that branch.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

        /*
         The amount is deliberately NOT capped at the outstanding balance. Paying more than
         what is owed leaves a credit in the customer's favour, which is real money they
         handed over: refusing it, or silently trimming it, would lose track of it.
         */
        CustomerPaymentEntity payment = CustomerPaymentEntity.builder()
                .customer(customer)
                .warehouse(warehouse)
                .paymentMethod(paymentMethod)
                .amount(request.amount())
                .paymentDate(request.paymentDate())
                .note(normalizeOptional(request.note()))
                .createdByUserEntity(createdBy)
                .build();

        return toResponse(customerPaymentRepository.save(payment));
    }

    /**
     * Collections in a date range, so the day's cash can be explained.
     *
     * <p>These amounts are not revenue and never appear in the sales reports, but the cash
     * is physically in the drawer. Without this view the till count has an unexplained
     * surplus.
     */
    @Transactional(readOnly = true)
    public List<CustomerPaymentResponse> search(LocalDate dateFrom, LocalDate dateTo, Long paymentMethodId) {
        return customerPaymentRepository.search(dateFrom, dateTo, paymentMethodId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CustomerPaymentResponse toResponse(CustomerPaymentEntity payment) {
        UserEntity user = payment.getCreatedByUserEntity();
        WarehouseEntity warehouse = payment.getWarehouse();

        return new CustomerPaymentResponse(
                payment.getId(),
                payment.getCustomer().getId(),
                payment.getCustomer().getName(),
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
