package com.empresa.serpent.transactions.service;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.repository.ExpenseRepository;
import com.empresa.serpent.transactions.repository.ExpenseSpecifications;
import com.empresa.serpent.transactions.web.dto.filter.ExpenseFilter;
import com.empresa.serpent.transactions.web.dto.response.ExpenseResponse;
import com.empresa.serpent.transactions.web.dto.response.GeneralExpensesSummaryResponse;
import com.empresa.serpent.transactions.web.mapper.ExpenseMapper;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseQueryService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final EntityManager entityManager;
    private final WarehouseScopeService warehouseScopeService;

    public Page<ExpenseResponse> search(ExpenseFilter filter, Pageable pageable) {
        WarehouseScope scope = warehouseScopeService.resolve(filter.warehouseId());
        if (scope.seesNothing()) {
            return Page.empty(pageable);
        }

        return expenseRepository
                .findAll(
                        ExpenseSpecifications.fromFilter(filter)
                                .and(ExpenseSpecifications.withinScope(scope)),
                        pageable)
                .map(expenseMapper::toResponse);
    }

    /**
     * What the branch filter left out: the general expenses matching every other filter.
     *
     * <p>Only meaningful while a branch filter is active — that is the case where the list
     * stops showing everything and the totals stop adding up. The caller decides when to
     * ask; this just answers.
     *
     * <p>Built from {@link ExpenseSpecifications#generalFromFilter} rather than a hand-written
     * query so the other filters cannot drift apart from the ones the listing applies. A
     * Specification cannot express an aggregate on its own, hence dropping to the Criteria
     * API here: the predicate is still the same object the listing uses.
     *
     * <p>Count and sum come back in ONE query. Two statements could straddle a concurrent
     * insert and report a count that does not match its own total.
     */
    public GeneralExpensesSummaryResponse summarizeGeneral(ExpenseFilter filter) {
        // General expenses are visible to everyone, so this aggregate needs no scoping:
        // by definition it only ever counts rows that belong to no branch.
        Specification<ExpenseEntity> specification = ExpenseSpecifications.generalFromFilter(filter);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<ExpenseEntity> root = query.from(ExpenseEntity.class);

        // The amount lives on the transaction, not on the expense, so this joins across.
        query.multiselect(
                cb.count(root),
                cb.coalesce(cb.sum(root.get("transaction").get("total")), BigDecimal.ZERO));

        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        Tuple row = entityManager.createQuery(query).getSingleResult();

        return new GeneralExpensesSummaryResponse(
                row.get(0, Long.class),
                row.get(1, BigDecimal.class));
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
