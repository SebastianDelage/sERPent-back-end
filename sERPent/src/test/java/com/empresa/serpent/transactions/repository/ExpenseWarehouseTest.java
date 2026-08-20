package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.service.ExpenseQueryService;
import com.empresa.serpent.transactions.web.dto.filter.ExpenseFilter;
import com.empresa.serpent.transactions.web.dto.response.GeneralExpensesSummaryResponse;
import com.empresa.serpent.transactions.web.mapper.ExpenseMapperImpl;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch attribution on expenses, and the gap it opens in the totals.
 *
 * <p>Fixture: two branches with one expense each, plus two general ones. The numbers are
 * chosen so "the branches do not add up to the total" is checkable by hand — 3000 + 2000
 * against a 10000 grand total.
 */
@DataJpaTest
@Import({ExpenseQueryService.class, ExpenseMapperImpl.class})
@ActiveProfiles("test")
@DisplayName("Expenses by warehouse")
class ExpenseWarehouseTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseQueryService expenseQueryService;

    private WarehouseEntity central;
    private WarehouseEntity branch;
    private WarehouseEntity closed;
    private ExpenseCategoryEntity rent;
    private ExpenseCategoryEntity services;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(UserEntity.builder()
                .name("Admin").username("admin_exp_wh").passwordHash("hash").active(true).build());

        central = persistWarehouse("Depósito Central", true);
        branch = persistWarehouse("Sucursal Norte", true);
        closed = persistWarehouse("Sucursal Cerrada", false);

        rent = persistCategory("Alquiler");
        services = persistCategory("Servicios");

        persistExpense("3000.00", central, rent);
        persistExpense("2000.00", branch, rent);
        // General: the accountant and the insurance belong to the company, not a branch.
        persistExpense("4000.00", null, services);
        persistExpense("1000.00", null, rent);
    }

    @Nested
    @DisplayName("the warehouse filter")
    class WarehouseFilter {

        @Test
        @DisplayName("Returns only that branch's expenses")
        void filtersToOneBranch() {
            List<ExpenseEntity> result = expenseRepository.findAll(
                    ExpenseSpecifications.fromFilter(filterFor(central.getId())));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTransaction().getTotal()).isEqualByComparingTo("3000.00");
        }

        @Test
        @DisplayName("Leaves the general expenses out: they belong to no branch")
        void generalExpensesAreExcluded() {
            List<ExpenseEntity> central = expenseRepository.findAll(
                    ExpenseSpecifications.fromFilter(filterFor(ExpenseWarehouseTest.this.central.getId())));
            List<ExpenseEntity> north = expenseRepository.findAll(
                    ExpenseSpecifications.fromFilter(filterFor(branch.getId())));

            BigDecimal branchesTotal = sumOf(central).add(sumOf(north));
            BigDecimal everything = sumOf(expenseRepository.findAll());

            // 3000 + 2000 = 5000, against 10000 overall. The 5000 difference is the general
            // expenses, and this gap is exactly what the listing has to state out loud.
            assertThat(branchesTotal).isEqualByComparingTo("5000.00");
            assertThat(everything).isEqualByComparingTo("10000.00");
            assertThat(branchesTotal).isNotEqualByComparingTo(everything);
        }

        @Test
        @DisplayName("Without a warehouse filter, everything comes back — general included")
        void noFilterReturnsEverything() {
            List<ExpenseEntity> result = expenseRepository.findAll(
                    ExpenseSpecifications.fromFilter(filterFor(null)));

            assertThat(result).hasSize(4);
        }
    }

    @Nested
    @DisplayName("the general-expenses specification")
    class GeneralExpenses {

        @Test
        @DisplayName("Matches only the ones with no branch")
        void matchesOnlyGeneral() {
            List<ExpenseEntity> result = expenseRepository.findAll(
                    ExpenseSpecifications.generalFromFilter(filterFor(central.getId())));

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(e -> e.getWarehouse() == null);
            assertThat(sumOf(result)).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("Still honours the other filters")
        void honoursTheOtherFilters() {
            // Only the general RENT expense: 1000, not the 4000 services one.
            ExpenseFilter filter = new ExpenseFilter(
                    null, rent.getId(), central.getId(), null, null, null);

            List<ExpenseEntity> result =
                    expenseRepository.findAll(ExpenseSpecifications.generalFromFilter(filter));

            assertThat(result).hasSize(1);
            assertThat(sumOf(result)).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("Ignores the warehouse filter: the answer is always the branchless ones")
        void ignoresTheWarehouseFilter() {
            BigDecimal forCentral = sumOf(expenseRepository.findAll(
                    ExpenseSpecifications.generalFromFilter(filterFor(central.getId()))));
            BigDecimal forBranch = sumOf(expenseRepository.findAll(
                    ExpenseSpecifications.generalFromFilter(filterFor(branch.getId()))));

            assertThat(forCentral).isEqualByComparingTo(forBranch);
        }
    }

    @Nested
    @DisplayName("the excluded-general summary")
    class GeneralSummary {

        @Test
        @DisplayName("Reports how many general expenses a branch filter left out, and for how much")
        void reportsCountAndTotal() {
            GeneralExpensesSummaryResponse summary =
                    expenseQueryService.summarizeGeneral(filterFor(central.getId()));

            // 4000 + 1000, the two that belong to the company rather than to a branch.
            assertThat(summary.count()).isEqualTo(2L);
            assertThat(summary.total()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("Narrows with the other filters")
        void narrowsWithTheOtherFilters() {
            GeneralExpensesSummaryResponse summary = expenseQueryService.summarizeGeneral(
                    new ExpenseFilter(null, rent.getId(), central.getId(), null, null, null));

            assertThat(summary.count()).isEqualTo(1L);
            assertThat(summary.total()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("Reports zero rather than null when nothing matches")
        void reportsZeroWhenNothingMatches() {
            GeneralExpensesSummaryResponse summary = expenseQueryService.summarizeGeneral(
                    new ExpenseFilter(null, null, null, null, null, "NO-EXISTE"));

            // SUM over an empty set is NULL in SQL; the COALESCE is what keeps this a number.
            assertThat(summary.count()).isZero();
            assertThat(summary.total()).isEqualByComparingTo("0");
        }
    }

    @Test
    @DisplayName("An expense can be booked against a closed branch")
    void expenseCanBelongToInactiveWarehouse() {
        // The last electricity bill turns up after the doors shut. Forcing it to "general"
        // would misattribute a cost that plainly belongs to that branch.
        persistExpense("700.00", closed, services);

        List<ExpenseEntity> result = expenseRepository.findAll(
                ExpenseSpecifications.fromFilter(filterFor(closed.getId())));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWarehouse().getActive()).isFalse();
    }

    // --- fixture helpers ---

    private ExpenseFilter filterFor(Long warehouseId) {
        return new ExpenseFilter(null, null, warehouseId, null, null, null);
    }

    private BigDecimal sumOf(List<ExpenseEntity> expenses) {
        return expenses.stream()
                .map(e -> e.getTransaction().getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private WarehouseEntity persistWarehouse(String name, boolean active) {
        return entityManager.persistAndFlush(
                WarehouseEntity.builder().name(name).active(active).build());
    }

    private ExpenseCategoryEntity persistCategory(String name) {
        return entityManager.persistAndFlush(
                ExpenseCategoryEntity.builder().name(name).active(true).build());
    }

    private void persistExpense(String total, WarehouseEntity warehouse, ExpenseCategoryEntity category) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.CONFIRMED)
                .total(new BigDecimal(total))
                .createdByUserEntity(user)
                .description("Gasto de prueba")
                .build());

        entityManager.persistAndFlush(ExpenseEntity.builder()
                .transaction(transaction)
                .warehouse(warehouse)
                .expenseCategory(category)
                .reimbursable(false)
                .build());
    }
}
