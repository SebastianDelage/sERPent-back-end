package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.transactions.domain.enums.TransactionStatus;

public record UpdateTransactionRequest(
        TransactionStatus status,
        String description
) {}
