package com.empresa.serpent.transactions.web.dto.response;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseResponse(

        Long id,

        Long transactionId,

        LocalDateTime transactionDate,

        BigDecimal total,

        String description,

        String receiptNumber,

        Boolean reimbursable,

        String notes,

        Long supplierId,

        String supplierName,

        Long expenseCategoryId,

        String expenseCategoryName,

        /** Null means this is a general (company-wide) expense, not a missing value. */
        Long warehouseId,

        String warehouseName,

        /**
         * Whether that branch is still open. Expenses can be booked against a closed branch
         * on purpose — the last bill always arrives after the doors shut — so the UI needs
         * to be able to say so instead of it looking like a mistake.
         */
        Boolean warehouseActive

) {
}