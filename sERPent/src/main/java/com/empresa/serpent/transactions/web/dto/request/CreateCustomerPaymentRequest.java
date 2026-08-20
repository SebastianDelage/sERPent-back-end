package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A customer paying down their balance. Not tied to any particular sale: the account is
 * kept by balance, so a partial payment is simply a smaller amount.
 */
public record CreateCustomerPaymentRequest(

        @NotNull(message = "Customer id cannot be null")
        Long customerId,

        @NotNull(message = "Payment method id cannot be null")
        Long paymentMethodId,

        @NotNull(message = "Amount cannot be null")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Payment date cannot be null")
        LocalDate paymentDate,

        String note
) {}
