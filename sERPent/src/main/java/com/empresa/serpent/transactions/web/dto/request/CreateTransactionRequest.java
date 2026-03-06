package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.transactions.domain.enums.TransactionType;

public record CreateTransactionRequest(
        TransactionType type,
        Long paymentMethodId,
        String description

) {}
