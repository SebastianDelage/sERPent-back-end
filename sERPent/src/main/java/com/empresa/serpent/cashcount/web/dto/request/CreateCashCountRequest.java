package com.empresa.serpent.cashcount.web.dto.request;

import com.empresa.serpent.shared.validation.MoneyLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * Closing the till: the two things only the person counting knows.
 *
 * <p>Notably absent: the expected amounts. They are recomputed server-side at the moment of
 * closing and frozen from there. Taking them from the client would let a stale screen — or
 * a hand-written request — decide what the shift was supposed to hold, which is the one
 * number the record exists to state.
 */
public record CreateCashCountRequest(

        /**
         * The branch being closed. Ignored when {@code terminalId} is set: the terminal
         * decides the branch.
         */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the branch. */
        Long terminalId,

        /**
         * Cash left in the drawer at the start of the shift to make change. Zero is a real
         * answer — some shifts start with an empty drawer — so it must be sent explicitly
         * rather than omitted.
         */
        @NotNull(message = "El fondo inicial es obligatorio.")
        @PositiveOrZero(message = "El fondo inicial no puede ser negativo.")
        @Digits(
                integer = MoneyLimits.INTEGER_DIGITS,
                fraction = MoneyLimits.FRACTION_DIGITS,
                message = "El fondo inicial no puede superar 9.999.999,99 y admite hasta dos decimales."
        )
        BigDecimal openingFloat,

        /**
         * What was actually counted, per payment method. Methods left out are taken as zero
         * counted, which is what an untouched posnet means.
         */
        @NotNull(message = "Los importes contados son obligatorios.")
        List<@Valid CashCountLineRequest> countedAmounts,

        String note
) {
}
