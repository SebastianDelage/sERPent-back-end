package com.empresa.serpent.transactions.web.dto.filter;

import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;

import java.time.LocalDateTime;

public record TransactionFilter(
        TransactionType type,
        TransactionStatus status,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        Long createdByUserId,
        Long warehouseId,
        Long paymentMethodId,
        String text
) {
}