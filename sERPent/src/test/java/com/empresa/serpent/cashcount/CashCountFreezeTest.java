package com.empresa.serpent.cashcount;

import com.empresa.serpent.cashcount.domain.entity.CashCountEntity;
import com.empresa.serpent.cashcount.domain.entity.CashCountLineEntity;
import com.empresa.serpent.cashcount.repository.CashCountRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stored count does not change its mind.
 *
 * <p>The expected amount, the method's name and its cash flag are all copied into the line
 * at close time. This test edits the catalog underneath a saved count and checks that the
 * count still reads the way it did when it was taken — the property that makes it a record
 * rather than a live query.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("A stored till count")
class CashCountFreezeTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CashCountRepository cashCountRepository;

    private WarehouseEntity central;
    private UserEntity user;
    private PaymentMethodEntity cash;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(UserEntity.builder()
                .name("Cajera").username("cajera_freeze").passwordHash("hash").active(true).build());
        central = entityManager.persistAndFlush(
                WarehouseEntity.builder().name("Depósito Central").active(true).build());
        cash = entityManager.persistAndFlush(PaymentMethodEntity.builder()
                .name("Efectivo").isCash(true).active(true).build());
    }

    @Test
    @DisplayName("Keeps the method's name and cash flag from the day it was taken")
    void freezesTheMethodAsItWas() {
        Long countId = persistCount(new BigDecimal("3100.0000"), new BigDecimal("3050.0000"));

        // The owner renames the method and moves the cash flag elsewhere.
        cash.setName("Contado");
        cash.setIsCash(false);
        entityManager.persistAndFlush(cash);
        entityManager.clear();

        CashCountLineEntity line = cashCountRepository.findById(countId).orElseThrow()
                .getLines().get(0);

        assertThat(line.getPaymentMethodName()).isEqualTo("Efectivo");
        assertThat(line.getIsCash()).isTrue();
        // The live catalog did change: the line is a copy, not a stale read.
        assertThat(line.getPaymentMethod().getName()).isEqualTo("Contado");
    }

    @Test
    @DisplayName("Keeps its expected amount even when the numbers behind it would say otherwise")
    void freezesTheExpectedAmount() {
        Long countId = persistCount(new BigDecimal("3100.0000"), new BigDecimal("3050.0000"));
        entityManager.clear();

        CashCountLineEntity line = cashCountRepository.findById(countId).orElseThrow()
                .getLines().get(0);

        assertThat(line.getExpectedAmount()).isEqualByComparingTo("3100.00");
        assertThat(line.getCountedAmount()).isEqualByComparingTo("3050.00");
        // Short by 50: the number the owner actually acted on.
        assertThat(line.getDifferenceAmount()).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("Is the anchor the next one reads, newest first")
    void theNewestCountIsTheAnchor() {
        LocalDateTime morning = LocalDateTime.of(2026, 3, 10, 14, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 3, 10, 21, 0);

        persistCountAt(morning);
        persistCountAt(evening);

        assertThat(cashCountRepository
                .findFirstByWarehouseIdOrderByClosedAtDescIdDesc(central.getId())
                .orElseThrow()
                .getClosedAt())
                .isEqualTo(evening);
    }

    private Long persistCount(BigDecimal expected, BigDecimal counted) {
        CashCountEntity count = CashCountEntity.builder()
                .warehouse(central)
                .createdByUserEntity(user)
                .closedAt(LocalDateTime.of(2026, 3, 10, 20, 0))
                .openingFloat(new BigDecimal("1000.0000"))
                .unattributedAmount(BigDecimal.ZERO)
                .unattributedCount(0)
                .lines(new java.util.ArrayList<>())
                .build();

        count.getLines().add(CashCountLineEntity.builder()
                .cashCount(count)
                .paymentMethod(cash)
                .paymentMethodName(cash.getName())
                .isCash(cash.getIsCash())
                .expectedAmount(expected)
                .countedAmount(counted)
                .differenceAmount(counted.subtract(expected))
                .build());

        return entityManager.persistAndFlush(count).getId();
    }

    private void persistCountAt(LocalDateTime closedAt) {
        entityManager.persistAndFlush(CashCountEntity.builder()
                .warehouse(central)
                .createdByUserEntity(user)
                .closedAt(closedAt)
                .openingFloat(BigDecimal.ZERO)
                .unattributedAmount(BigDecimal.ZERO)
                .unattributedCount(0)
                .lines(List.of())
                .build());
    }
}
