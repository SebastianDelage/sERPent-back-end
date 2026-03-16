package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ExpenseQueryService;
import com.empresa.serpent.transactions.service.ExpenseApplicationService;
import com.empresa.serpent.transactions.web.dto.filter.ExpenseFilter;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.ExpenseResponse;
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
            @RequestParam(required = false) Boolean reimbursable,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) String receiptNumber,
            Pageable pageable
    ) {
        ExpenseFilter filter = new ExpenseFilter(
                supplierId,
                expenseCategoryId,
                reimbursable,
                transactionId,
                receiptNumber
        );

        return expenseQueryService.search(filter, pageable);
    }
}