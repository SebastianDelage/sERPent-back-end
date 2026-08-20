package com.empresa.serpent.transactions.web.dto.response;

import com.empresa.serpent.transactions.domain.enums.AccountMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a current-account statement.
 *
 * @param amount         signed against the balance: positive raises the debt, negative lowers it
 * @param runningBalance the balance after applying this movement
 */
public record AccountMovementResponse(
        LocalDate date,
        AccountMovementType type,
        Long referenceId,
        String description,
        BigDecimal amount,
        BigDecimal runningBalance
) {}
