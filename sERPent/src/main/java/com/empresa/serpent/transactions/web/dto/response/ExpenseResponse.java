package com.empresa.serpent.transactions.web.dto.response;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseResponse(

        Long id,

        Long transactionId,

        LocalDateTime transactionDate,

        BigDecimal total,

        String description,

        String receiptNumber,

        Boolean reimbursable,

        String notes,

        Long supplierId,

        String supplierName,

        Long expenseCategoryId,

        String expenseCategoryName

) {
}