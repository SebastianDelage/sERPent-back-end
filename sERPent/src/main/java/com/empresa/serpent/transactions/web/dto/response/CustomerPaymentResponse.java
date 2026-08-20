package com.empresa.serpent.transactions.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerPaymentResponse(
        Long id,
        Long customerId,
        String customerName,
        Long paymentMethodId,
        String paymentMethodName,
        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        String createdByUserName,
        LocalDateTime createdAt
) {}
