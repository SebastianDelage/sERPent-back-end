package com.empresa.serpent.transactions.service;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import com.empresa.serpent.transactions.repository.ExpenseCategoryRepository;
import com.empresa.serpent.transactions.web.dto.request.ExpenseCategoryCreateRequest;
import com.empresa.serpent.transactions.web.dto.request.ExpenseCategoryUpdateRequest;
import com.empresa.serpent.transactions.web.dto.response.ExpenseCategoryResponse;
import com.empresa.serpent.transactions.web.mapper.ExpenseCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseCategoryMapper expenseCategoryMapper;

    @Transactional
    public ExpenseCategoryResponse create(ExpenseCategoryCreateRequest request) {
        validateName(request.name(), null);

        ExpenseCategoryEntity entity = expenseCategoryMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeFields(entity);

        ExpenseCategoryEntity saved = expenseCategoryRepository.save(entity);
        return expenseCategoryMapper.toResponse(saved);
    }

    @Transactional
    public ExpenseCategoryResponse update(Long id, ExpenseCategoryUpdateRequest request) {
        validateName(request.name(), id);

        ExpenseCategoryEntity entity = expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expense category not found: " + id));

        expenseCategoryMapper.updateEntityFromRequest(request, entity);
        normalizeFields(entity);

        ExpenseCategoryEntity saved = expenseCategoryRepository.save(entity);
        return expenseCategoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ExpenseCategoryResponse findById(Long id) {
        ExpenseCategoryEntity entity = expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expense category not found: " + id));

        return expenseCategoryMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> findAllActive() {
        return expenseCategoryRepository.findByActiveTrue().stream()
                .map(expenseCategoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> searchActiveByName(String name) {
        if (name == null || name.isBlank()) {
            return findAllActive();
        }

        return expenseCategoryRepository.findByActiveTrueAndNameContainingIgnoreCase(name.trim()).stream()
                .map(expenseCategoryMapper::toResponse)
                .toList();
    }

    private void validateName(String name, Long currentCategoryId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Expense category name cannot be blank");
        }

        expenseCategoryRepository.findByNameIgnoreCase(name.trim())
                .ifPresent(existing -> {
                    if (currentCategoryId == null || !existing.getId().equals(currentCategoryId)) {
                        throw new IllegalArgumentException("Expense category name already exists: " + name.trim());
                    }
                });
    }

    private void normalizeFields(ExpenseCategoryEntity entity) {
        entity.setName(normalizeRequired(entity.getName()));
        entity.setDescription(normalizeOptional(entity.getDescription()));
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}