package com.empresa.serpent.transactions.web.dto.response;

import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param warehouseNames the branches this transaction touched. Usually one; a TRANSFER has
 *                       two, because it happened at both ends. Empty for a general expense,
 *                       which belongs to the company and to no branch.
 */
public record TransactionListResponse(
        Long id,
        LocalDateTime date,
        TransactionType type,
        TransactionStatus status,
        BigDecimal total,
        List<String> warehouseNames
) {
}