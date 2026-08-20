package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ExpenseQueryService;
import com.empresa.serpent.transactions.service.ExpenseApplicationService;
import com.empresa.serpent.transactions.web.dto.filter.ExpenseFilter;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.ExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.GeneralExpensesSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseApplicationService expenseService;
    private final ExpenseQueryService expenseQueryService;

    @PostMapping
    public CreateExpenseResponse create(@Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.createExpense(request);
    }

    @GetMapping("/{id}")
    public ExpenseResponse findById(@PathVariable Long id) {
        return expenseQueryService.findById(id);
    }

    @GetMapping("/transaction/{transactionId}")
    public ExpenseResponse findByTransactionId(@PathVariable Long transactionId) {
        return expenseQueryService.findByTransactionId(transactionId);
    }

    @GetMapping
    public Page<ExpenseResponse> search(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long expenseCategoryId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Boolean reimbursable,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) String receiptNumber,
            Pageable pageable
    ) {
        return expenseQueryService.search(
                filterOf(supplierId, expenseCategoryId, warehouseId, reimbursable, transactionId, receiptNumber),
                pageable);
    }

    /**
     * The general expenses excluded by a branch filter, with the other filters still applied.
     *
     * <p>A branch filter hides the company-wide expenses, so the branches never add up to
     * the total. This is what lets the UI say so out loud instead of leaving the reader to
     * wonder why the numbers do not close.
     *
     * <p>{@code warehouseId} is accepted and ignored on purpose: the answer is always about
     * the expenses with NO branch, and the caller passing its whole filter through unchanged
     * is simpler than making it strip one field.
     */
    @GetMapping("/general-summary")
    public GeneralExpensesSummaryResponse generalSummary(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long expenseCategoryId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Boolean reimbursable,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) String receiptNumber
    ) {
        return expenseQueryService.summarizeGeneral(
                filterOf(supplierId, expenseCategoryId, warehouseId, reimbursable, transactionId, receiptNumber));
    }

    private ExpenseFilter filterOf(Long supplierId,
                                   Long expenseCategoryId,
                                   Long warehouseId,
                                   Boolean reimbursable,
                                   Long transactionId,
                                   String receiptNumber) {
        return new ExpenseFilter(
                supplierId,
                expenseCategoryId,
                warehouseId,
                reimbursable,
                transactionId,
                receiptNumber
        );
    }
}