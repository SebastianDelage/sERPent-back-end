package com.empresa.serpent.transactions.service;

import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import com.empresa.serpent.transactions.repository.ExpenseRepository;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.repository.TransactionSpecifications;
import com.empresa.serpent.transactions.web.dto.filter.TransactionFilter;
import com.empresa.serpent.transactions.web.dto.response.TransactionDetailResponse;
import com.empresa.serpent.transactions.web.dto.response.TransactionListResponse;
import com.empresa.serpent.transactions.web.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final WarehouseScopeService warehouseScopeService;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ExpenseRepository expenseRepository;

    public Page<TransactionListResponse> search(TransactionFilter filter, Pageable pageable) {
        validateFilter(filter);

        WarehouseScope scope = warehouseScopeService.resolve(filter.warehouseId());
        if (scope.seesNothing()) {
            return Page.empty(pageable);
        }

        Specification<TransactionEntity> spec = TransactionSpecifications.fromFilter(filter)
                .and(TransactionSpecifications.withinScope(scope));

        Page<TransactionEntity> page = transactionRepository.findAll(spec, pageable);

        Map<Long, List<String>> warehousesByTransaction = warehouseNamesFor(
                page.getContent().stream().map(TransactionEntity::getId).toList());

        return page.map(entity -> withWarehouses(
                transactionMapper.toListResponse(entity),
                warehousesByTransaction.getOrDefault(entity.getId(), List.of())));
    }

    /**
     * The branches behind a page of transactions, in two queries rather than two per row.
     *
     * <p>Movements cover everything that moves stock — a transfer contributing both of its
     * ends — and expenses are looked up separately because they move money instead and
     * leave no movement behind.
     */
    private Map<Long, List<String>> warehouseNamesFor(List<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> byTransaction = new LinkedHashMap<>();

        Stream.concat(
                        inventoryMovementRepository.findWarehouseNamesByTransactionIds(transactionIds).stream(),
                        expenseRepository.findWarehouseNamesByTransactionIds(transactionIds).stream())
                .forEach(row -> byTransaction
                        .computeIfAbsent(row.getTransactionId(), key -> new ArrayList<>())
                        .add(row.getWarehouseName()));

        byTransaction.values().forEach(names -> {
            List<String> distinct = names.stream().distinct().sorted().toList();
            names.clear();
            names.addAll(distinct);
        });

        return byTransaction;
    }

    private TransactionListResponse withWarehouses(TransactionListResponse response, List<String> names) {
        return new TransactionListResponse(
                response.id(),
                response.date(),
                response.type(),
                response.status(),
                response.total(),
                names);
    }

    /**
     * One transaction, refused when it belongs to a branch the caller may not see.
     *
     * <p>Re-runs the same scope predicate the listing uses rather than re-deriving the rule:
     * scoping the list and leaving the detail open would just move the leak one click away.
     */
    public TransactionDetailResponse getById(Long id) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));

        WarehouseScope scope = warehouseScopeService.resolve(null);
        if (!scope.unrestricted()) {
            boolean visible = transactionRepository.findAll(
                            TransactionSpecifications.withinScope(scope)
                                    .and((root, query, cb) -> cb.equal(root.get("id"), id)))
                    .size() == 1;

            if (!visible) {
                throw new ForbiddenException("No tenés permiso para ver los datos de ese depósito.");
            }
        }

        return transactionMapper.toDetailResponse(entity);
    }

    private void validateFilter(TransactionFilter filter) {
        if (filter == null) {
            return;
        }

        if (filter.dateFrom() != null
                && filter.dateTo() != null
                && filter.dateFrom().isAfter(filter.dateTo())) {
            throw new IllegalArgumentException("dateFrom cannot be after dateTo");
        }
    }
}