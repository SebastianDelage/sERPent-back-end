package com.empresa.serpent.transactions.web.dto.response;

public record CreateSaleResponse(
        Long transactionId,
        Long saleId,
        String status,
        String message
) {}
