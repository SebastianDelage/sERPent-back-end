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

        /** The branch whose till took the money. Null only on rows recorded before it was asked for. */
        Long warehouseId,
        String warehouseName,

        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        String createdByUserName,
        LocalDateTime createdAt
) {}
