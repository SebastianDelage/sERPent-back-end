package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paying down what we owe a supplier. Not tied to any particular purchase: the account is
 * kept by balance, so a partial payment is simply a smaller amount.
 */
public record CreateSupplierPaymentRequest(

        @NotNull(message = "Supplier id cannot be null")
        Long supplierId,

        @NotNull(message = "Payment method id cannot be null")
        Long paymentMethodId,

        /**
         * The branch whose till the money comes out of. Required — the shift count is per
         * branch, and a payment with no branch could not be subtracted anywhere.
         *
         * <p>Ignored when {@code terminalId} is set: the terminal decides the branch.
         */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the branch. */
        Long terminalId,

        @NotNull(message = "Amount cannot be null")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Payment date cannot be null")
        LocalDate paymentDate,

        String note
) {}
