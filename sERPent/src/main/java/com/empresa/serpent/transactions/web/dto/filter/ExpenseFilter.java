package com.empresa.serpent.transactions.web.dto.filter;

/**
 * @param warehouseId restricts to one branch. General expenses (those with no warehouse)
 *                    fall outside any branch, so this filter excludes them — which is why
 *                    the listing also reports them separately.
 */
public record ExpenseFilter(
        Long supplierId,
        Long expenseCategoryId,
        Long warehouseId,
        Boolean reimbursable,
        Long transactionId,
        String receiptNumber
) {
}
