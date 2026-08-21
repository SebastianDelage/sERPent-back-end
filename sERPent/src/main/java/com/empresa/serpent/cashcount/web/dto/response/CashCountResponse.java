package com.empresa.serpent.cashcount.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A stored till count, exactly as it was frozen.
 *
 * @param periodFrom          where this count started summing. NULL means it was the
 *                            branch's first, covering everything up to {@code closedAt}.
 *                            The screen has to show it: a large total with no period beside
 *                            it reads as a bug.
 * @param unattributedAmount  money that moved in the period but belonged to no payment
 *                            method, and so is in none of the lines. Kept because it may be
 *                            the whole explanation for a difference.
 * @param totalDifference     the sum of the lines' differences. Derived, not stored: the
 *                            lines are the record.
 */
public record CashCountResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        String createdByUserName,
        LocalDateTime closedAt,
        LocalDateTime periodFrom,
        BigDecimal openingFloat,
        List<CashCountLineResponse> lines,
        BigDecimal totalExpected,
        BigDecimal totalCounted,
        BigDecimal totalDifference,
        BigDecimal unattributedAmount,
        int unattributedCount,
        String note
) {
}
