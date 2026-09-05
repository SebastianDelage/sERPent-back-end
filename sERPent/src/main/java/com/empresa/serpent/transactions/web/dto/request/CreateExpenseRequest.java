package com.empresa.serpent.transactions.web.dto.request;


import com.empresa.serpent.shared.validation.MoneyLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateExpenseRequest(

        @NotNull(message = "El usuario es obligatorio.")
        Long createdByUserId,

        Long paymentMethodId,

        Long supplierId,

        /**
         * The branch this expense belongs to. Omit it for a company-wide expense — absent
         * means GENERAL, which is a valid answer and not an oversight.
         */
        Long warehouseId,

        @NotNull(message = "La categoría es obligatoria.")
        Long expenseCategoryId,

        @NotNull(message = "El total es obligatorio.")
        @PositiveOrZero(message = "El total no puede ser negativo.")
        @Digits(
                integer = MoneyLimits.INTEGER_DIGITS,
                fraction = MoneyLimits.FRACTION_DIGITS,
                message = "El total del gasto no puede superar 9.999.999,99 y admite hasta dos decimales."
        )
        BigDecimal total,

        @Size(max = 80, message = "El número de comprobante no puede tener más de 80 caracteres.")
        String receiptNumber,

        String description,

        Boolean reimbursable,

        String notes
) {
}