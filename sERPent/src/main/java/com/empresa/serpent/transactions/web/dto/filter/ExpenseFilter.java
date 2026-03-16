package com.empresa.serpent.transactions.web.dto.filter;

public record ExpenseFilter(
        Long supplierId,
        Long expenseCategoryId,
        Boolean reimbursable,
        Long transactionId,
        String receiptNumber
) {
}