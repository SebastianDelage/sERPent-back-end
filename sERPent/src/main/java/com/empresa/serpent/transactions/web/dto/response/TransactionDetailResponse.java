package com.empresa.serpent.transactions.web.dto.response;

import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionDetailResponse(
        Long id,
        LocalDateTime date,
        TransactionType type,
        TransactionStatus status,
        BigDecimal total,
        String description,
        Long paymentMethodId,
        String paymentMethodName,
        Long createdByUserId,
        String createdByUsername,
        Long saleId,
        List<TransactionItemResponse> details
) {
}