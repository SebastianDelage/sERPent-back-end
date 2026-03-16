package com.empresa.serpent.transactions.service;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.repository.ExpenseRepository;
import com.empresa.serpent.transactions.repository.ExpenseSpecifications;
import com.empresa.serpent.transactions.web.dto.filter.ExpenseFilter;
import com.empresa.serpent.transactions.web.dto.response.ExpenseResponse;
import com.empresa.serpent.transactions.web.mapper.ExpenseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseQueryService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;

    public Page<ExpenseResponse> search(ExpenseFilter filter, Pageable pageable) {
        return expenseRepository.findAll(ExpenseSpecifications.fromFilter(filter), pageable)
                .map(expenseMapper::toResponse);
    }

    public ExpenseResponse findById(Long id) {
        ExpenseEntity entity = expenseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expense not found: " + id));

        return expenseMapper.toResponse(entity);
    }

    public ExpenseResponse findByTransactionId(Long transactionId) {
        ExpenseEntity entity = expenseRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NotFoundException("Expense not found for transaction: " + transactionId));

        return expenseMapper.toResponse(entity);
    }
}