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

        /** The branch whose till the money came out of. Null only on rows recorded before it was asked for. */
        Long warehouseId,
        String warehouseName,

        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        String createdByUserName,
        LocalDateTime createdAt
) {}
