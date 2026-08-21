package com.empresa.serpent.cashcount.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What the till should hold right now, for the shift in progress.
 *
 * @param periodFrom          where the shift started: the previous close for this branch.
 *                            NULL means there is no previous close, so this covers every
 *                            record there is. The screen must say so — a large number with
 *                            no period next to it looks like a bug.
 * @param periodTo            the moment these figures were computed.
 * @param methods             one row per payment method with movement. Cash carries the
 *                            opening float and the money that left the drawer; the others
 *                            only carry what came in and what was refunded.
 * @param cashConfigured      false when no payment method is flagged as cash. The cash
 *                            figures are then absent rather than zero, and {@code warnings}
 *                            says what to do about it.
 * @param unattributedAmount  money that moved but belongs to no method: returns and expenses
 *                            recorded before the method was asked for. NOT included in any
 *                            row — nobody knows which one it belongs to.
 * @param warnings            plain-Spanish sentences about anything that makes these numbers
 *                            less than the whole story. Empty when there is nothing to say.
 */
public record ExpectedCashCountResponse(
        Long warehouseId,
        String warehouseName,
        LocalDateTime periodFrom,
        LocalDateTime periodTo,
        List<ExpectedCashCountMethodResponse> methods,
        boolean cashConfigured,
        BigDecimal unattributedAmount,
        long unattributedCount,
        List<String> warnings
) {
}
