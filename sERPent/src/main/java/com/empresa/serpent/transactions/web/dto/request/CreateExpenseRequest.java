package com.empresa.serpent.transactions.web.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateExpenseRequest(

        @NotNull(message = "CreatedByUserId cannot be null")
        Long createdByUserId,

        Long paymentMethodId,

        Long supplierId,

        @NotNull(message = "ExpenseCategoryId cannot be null")
        Long expenseCategoryId,

        @NotNull(message = "Total cannot be null")
        @PositiveOrZero(message = "Total cannot be negative")
        BigDecimal total,

        @Size(max = 80, message = "Receipt number cannot be longer than 80 characters")
        String receiptNumber,

        String description,

        Boolean reimbursable,

        String notes
) {
}