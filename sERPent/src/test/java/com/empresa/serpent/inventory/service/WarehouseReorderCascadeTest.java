package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.web.dto.request.UpsertProductWarehouseMinimumStockRequest;
import com.empresa.serpent.inventory.web.dto.response.ProductWarehouseMinimumStockResponse;
import com.empresa.serpent.shared.exception.ValidationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The per-warehouse reorder cascade, and the invariant that survives it.
 *
 * <p>THE INVARIANT: at any warehouse, the reorder point that applies may not sit below the
 * minimum that applies. The interesting cases are the MIXED ones — a warehouse that owns one
 * of the two figures and inherits the other — because there the comparison has one foot in
 * the override row and one in the product, and any check that only looks at the row misses
 * them entirely.
 *
 * <p>Product fixture: floor 20, reorder point 30. Both sides of every mixed case are
 * therefore known by heart while reading the assertions.
 */
@DataJpaTest
@Import(ProductWarehouseMinimumStockService.class)
@ActiveProfiles("test")
@DisplayName("Per-warehouse reorder cascade")
class WarehouseReorderCascadeTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductWarehouseMinimumStockService service;

    private ProductEntity product;
    private WarehouseEntity central;

    @BeforeEach
    void setUp() {
        product = entityManager.persistAndFlush(ProductEntity.builder()
                .name("Pollo entero")
                .sku("POLLO_CASCADE")
                .price(new BigDecimal("2500.0000"))
                .active(true)
                .minimumStock(new BigDecimal("20.000"))
                .reorderPoint(new BigDecimal("30.000"))
                .reorderQuantity(new BigDecimal("80.000"))
                .build());

        central = entityManager.persistAndFlush(
                WarehouseEntity.builder().name("Depósito Central").active(true).build());
    }

    @Nested
    @DisplayName("resolving each figure")
    class Resolving {

        @Test
        @DisplayName("With no override, every figure is the product's")
        void inheritsEverything() {
            ProductWarehouseMinimumStockResponse row = only();

            assertThat(row.inherited()).isTrue();
            assertThat(row.ownMinimum()).isNull();
            assertThat(row.effectiveMinimum()).isEqualByComparingTo("20.000");
            assertThat(row.effectiveReorderPoint()).isEqualByComparingTo("30.000");
            assertThat(row.effectiveReorderQuantity()).isEqualByComparingTo("80.000");
        }

        @Test
        @DisplayName("The three cascade independently: own reorder point, inherited floor")
        void figuresCascadeIndependently() {
            // The branch sells more, so it orders earlier — but the floor it must not break
            // through has not changed, and it should not have to restate it.
            upsert(null, "100.000", null);

            ProductWarehouseMinimumStockResponse row = only();

            assertThat(row.ownReorderPoint()).isEqualByComparingTo("100.000");
            assertThat(row.effectiveReorderPoint()).isEqualByComparingTo("100.000");
            assertThat(row.ownMinimum()).isNull();
            assertThat(row.effectiveMinimum()).isEqualByComparingTo("20.000");
        }

        @Test
        @DisplayName("Clearing every figure removes the override instead of storing an empty one")
        void clearingEverythingDeletesTheRow() {
            upsert("50.000", "120.000", null);
            assertThat(only().inherited()).isFalse();

            upsert(null, null, null);

            // "Inherit everything" and "no override" are the same statement, so only one of
            // them is representable.
            assertThat(only().inherited()).isTrue();
            assertThat(only().effectiveMinimum()).isEqualByComparingTo("20.000");
        }
    }

    @Nested
    @DisplayName("the invariant, on the resolved pair")
    class Invariant {

        @Test
        @DisplayName("Own minimum above an INHERITED reorder point is refused")
        void ownMinimumAgainstInheritedReorderPoint() {
            // Only the minimum is sent. Compared against the product's 30, which the row
            // does not contain — a check reading the row alone would see nothing wrong.
            assertThatThrownBy(() -> upsert("50.000", null, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Depósito Central")
                    .hasMessageContaining("se heredan del producto");
        }

        @Test
        @DisplayName("Own reorder point below an INHERITED minimum is refused")
        void ownReorderPointAgainstInheritedMinimum() {
            // The mirror image: only the reorder point is sent, compared against the
            // product's floor of 20.
            assertThatThrownBy(() -> upsert(null, "10.000", null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Both owned and inconsistent is refused")
        void bothOwnedAndInconsistent() {
            assertThatThrownBy(() -> upsert("80.000", "50.000", null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Both owned and consistent is accepted")
        void bothOwnedAndConsistent() {
            upsert("50.000", "120.000", "300.000");

            ProductWarehouseMinimumStockResponse row = only();

            assertThat(row.effectiveMinimum()).isEqualByComparingTo("50.000");
            assertThat(row.effectiveReorderPoint()).isEqualByComparingTo("120.000");
            assertThat(row.effectiveReorderQuantity()).isEqualByComparingTo("300.000");
        }

        @Test
        @DisplayName("Equal is allowed: the rule is not-below, not strictly-above")
        void equalIsAllowed() {
            upsert("40.000", "40.000", null);

            assertThat(only().effectiveReorderPoint()).isEqualByComparingTo("40.000");
        }
    }

    @Nested
    @DisplayName("the product moving underneath")
    class ProductMovingUnderneath {

        @Test
        @DisplayName("Raising the product's minimum is refused when it would undercut a branch")
        void raisingProductMinimumBreaksAnOverride() {
            // The branch owns only its reorder point, at 25. Consistent against the
            // product's floor of 20 — and broken the moment that floor rises to 60.
            upsert(null, "25.000", null);

            assertThatThrownBy(() -> service.validateOverridesAgainst(
                    product.getId(), new BigDecimal("60.000"), new BigDecimal("70.000")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Depósito Central");
        }

        @Test
        @DisplayName("Lowering the product's reorder point is refused when a branch inherits it")
        void loweringProductReorderPointBreaksAnOverride() {
            // The branch owns only its floor, at 25, and inherits the reorder point. Valid
            // right now against the product's 30 — and broken the moment that drops to 15.
            upsert("25.000", null, null);

            assertThatThrownBy(() -> service.validateOverridesAgainst(
                    product.getId(), new BigDecimal("20.000"), new BigDecimal("15.000")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Depósito Central");
        }

        @Test
        @DisplayName("A change that keeps every branch consistent goes through")
        void harmlessChangeIsAccepted() {
            upsert(null, "100.000", null);

            service.validateOverridesAgainst(
                    product.getId(), new BigDecimal("40.000"), new BigDecimal("60.000"));
        }

        @Test
        @DisplayName("A branch that owns BOTH figures is immune to the product moving")
        void fullyOwnedOverrideIsUnaffected() {
            upsert("50.000", "120.000", null);

            // Neither figure comes from the product any more, so nothing the product does
            // can break this branch.
            service.validateOverridesAgainst(
                    product.getId(), new BigDecimal("999.000"), new BigDecimal("999.000"));
        }
    }

    // --- helpers ---------------------------------------------------------------------

    private void upsert(String minimum, String reorderPoint, String reorderQuantity) {
        service.upsert(product.getId(), new UpsertProductWarehouseMinimumStockRequest(
                central.getId(),
                minimum == null ? null : new BigDecimal(minimum),
                reorderPoint == null ? null : new BigDecimal(reorderPoint),
                reorderQuantity == null ? null : new BigDecimal(reorderQuantity)));
        entityManager.flush();
        entityManager.clear();
    }

    /** The single active warehouse's resolved row. */
    private ProductWarehouseMinimumStockResponse only() {
        return service.findByProduct(product.getId()).stream()
                .filter(row -> row.warehouseId().equals(central.getId()))
                .findFirst()
                .orElseThrow();
    }
}
