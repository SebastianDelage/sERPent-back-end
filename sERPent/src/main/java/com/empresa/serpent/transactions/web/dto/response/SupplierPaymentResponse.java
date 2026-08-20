package com.empresa.serpent.transactions.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SupplierPaymentResponse(
        Long id,
        Long supplierId,
        String supplierName,
        Long paymentMethodId,
        String paymentMethodName,
        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        String createdByUserName,
        LocalDateTime createdAt
) {}
