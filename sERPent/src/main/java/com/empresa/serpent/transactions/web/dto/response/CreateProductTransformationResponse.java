package com.empresa.serpent.transactions.web.dto.response;

public record CreateProductTransformationResponse(
        Long transactionId,
        Long transformationId,
        String status,
        String message
) {
}