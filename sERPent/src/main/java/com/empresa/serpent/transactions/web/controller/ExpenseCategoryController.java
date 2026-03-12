package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ExpenseCategoryService;
import com.empresa.serpent.transactions.web.dto.request.ExpenseCategoryCreateRequest;
import com.empresa.serpent.transactions.web.dto.request.ExpenseCategoryUpdateRequest;
import com.empresa.serpent.transactions.web.dto.response.ExpenseCategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @PostMapping
    public ExpenseCategoryResponse create(@Valid @RequestBody ExpenseCategoryCreateRequest request) {
        return expenseCategoryService.create(request);
    }

    @PutMapping("/{id}")
    public ExpenseCategoryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseCategoryUpdateRequest request
    ) {
        return expenseCategoryService.update(id, request);
    }

    @GetMapping("/{id}")
    public ExpenseCategoryResponse findById(@PathVariable Long id) {
        return expenseCategoryService.findById(id);
    }

    @GetMapping
    public List<ExpenseCategoryResponse> findAllActive(
            @RequestParam(required = false) String name
    ) {
        return expenseCategoryService.searchActiveByName(name);
    }
}