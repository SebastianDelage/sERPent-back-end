package com.empresa.serpent.transactions.web.dto.response;

public record CreateExpenseResponse(
        Long transactionId,
        Long expenseId,
        String status,
        String message
) {
}