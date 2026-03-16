package com.empresa.serpent.transactions.web.dto.response;

public record CreatePurchaseResponse(
        Long transactionId,
        Long purchaseId,
        String status,
        String message
) {
}