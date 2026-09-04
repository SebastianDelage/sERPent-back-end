package com.empresa.serpent.transactions.web.dto.response;

import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionDetailResponse(
        Long id,
        LocalDateTime date,
        TransactionType type,
        TransactionStatus status,
        BigDecimal total,
        String description,
        Long paymentMethodId,
        String paymentMethodName,
        Long createdByUserId,
        String createdByUsername,
        Long saleId,
        Long warehouseId,
        String warehouseName,
        /**
         * Every branch this transaction touched, by name. Usually one; a TRANSFER has two.
         *
         * <p>Derived from the inventory movements it left behind, the same way and with the
         * same query {@code TransactionListResponse} already used — no new column, and nothing
         * stored. It replaces what the generated {@code description} sentence used to carry:
         * for a transfer, its two ends were the only thing in that sentence that was not
         * already on the screen.
         *
         * <p>{@code warehouseId}/{@code warehouseName} above stay: they are the SALE's branch
         * specifically, which the return flow needs as an id, and they come off the sale row
         * rather than off the movements.
         */
        List<String> warehouseNames,
        /**
         * Sale-only: the sale was taken on account and nothing was collected.
         *
         * <p>Sent explicitly rather than left for the client to infer from a missing payment
         * method. The return dialog needs it: a return against a credit sale hands no money
         * back, so it must not ask which method the refund left through.
         */
        Boolean onCredit,
        /*
         The sale-wide manual adjustment: one figure for the whole sale. Distinct from
         the per-line payment-method surcharge, which each TransactionItemResponse
         carries in its own base/applied fields.
         */
        /** Sale-only, null for every other transaction type. */
        AdjustmentType adjustmentType,
        /** Sale-only: the cashier's raw input, signed. */
        BigDecimal adjustmentValue,
        /** Sale-only: the resolved, frozen adjustment amount. */
        BigDecimal adjustmentAmount,
        List<TransactionItemResponse> details
) {
}