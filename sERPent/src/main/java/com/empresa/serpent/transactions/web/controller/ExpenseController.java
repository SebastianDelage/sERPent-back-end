package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ExpenseService;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.ExpenseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public CreateExpenseResponse create(@Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.createExpense(request);
    }

    @GetMapping("/{id}")
    public ExpenseResponse findById(@PathVariable Long id) {
        return expenseService.findById(id);
    }

    @GetMapping("/transaction/{transactionId}")
    public ExpenseResponse findByTransactionId(@PathVariable Long transactionId) {
        return expenseService.findByTransactionId(transactionId);
    }

    @GetMapping("/receipt/{receiptNumber}")
    public ExpenseResponse findByReceiptNumber(@PathVariable String receiptNumber) {
        return expenseService.findByReceiptNumber(receiptNumber);
    }

    @GetMapping
    public List<ExpenseResponse> findAllByFilters(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean reimbursable
    ) {
        if (supplierId != null) {
            return expenseService.findBySupplierId(supplierId);
        }

        if (categoryId != null) {
            return expenseService.findByExpenseCategoryId(categoryId);
        }

        if (Boolean.TRUE.equals(reimbursable)) {
            return expenseService.findReimbursable();
        }

        throw new IllegalArgumentException("At least one filter must be provided");
    }
}