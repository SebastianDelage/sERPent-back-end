package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.StockStatusFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockPageFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Filters for the per-warehouse stock view, applied in the query so paging happens over
 * the filtered set rather than over everything.
 */
public final class InventoryStockSnapshotSpecifications {

    private InventoryStockSnapshotSpecifications() {
    }

    /** Partial name, or exact SKU / barcode — the same criterion as ProductRepository.search. */
    public static Specification<InventoryStockSnapshotEntity> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }

            String term = search.trim();
            Path<ProductEntity> product = root.get("product");

            return cb.or(
                    cb.like(cb.lower(product.get("name")), "%" + term.toLowerCase() + "%"),
                    cb.equal(cb.lower(product.get("sku")), term.toLowerCase()),
                    cb.equal(product.get("barcode"), term)
            );
        };
    }

    public static Specification<InventoryStockSnapshotEntity> hasWarehouseId(Long warehouseId) {
        return (root, query, cb) ->
                warehouseId == null
                        ? cb.conjunction()
                        : cb.equal(root.<WarehouseEntity>get("warehouse").get("id"), warehouseId);
    }

    /**
     * The stock status, with {@code BELOW_MINIMUM} resolving the minimum in cascade.
     *
     * <p>The cascade is a COALESCE over a correlated subquery: the per-warehouse override
     * for this exact (product, warehouse) when one exists, the product's own minimum
     * otherwise.
     *
     * <p>The {@code IS NOT NULL} guard is explicit on purpose. A product with no minimum
     * at either level would also be excluded by SQL's NULL comparison semantics, but
     * relying on that is the kind of thing that keeps working until someone edits the
     * query without knowing why it was safe.
     */
    public static Specification<InventoryStockSnapshotEntity> hasStatus(StockStatusFilter status) {
        return (root, query, cb) -> {
            if (status == null || status == StockStatusFilter.ALL) {
                return cb.conjunction();
            }

            Path<BigDecimal> stock = root.get("currentStock");

            return switch (status) {
                case OUT_OF_STOCK -> cb.lessThanOrEqualTo(stock, BigDecimal.ZERO);
                case IN_STOCK -> cb.greaterThan(stock, BigDecimal.ZERO);
                case BELOW_MINIMUM -> {
                    Expression<BigDecimal> effectiveMinimum = effectiveMinimum(root, query, cb);
                    yield cb.and(
                            cb.isNotNull(effectiveMinimum),
                            // "At or below" counts as low, matching the low-stock report.
                            cb.lessThanOrEqualTo(stock, effectiveMinimum)
                    );
                }
                // ALL is handled above; listed so the switch stays exhaustive.
                case ALL -> cb.conjunction();
            };
        };
    }

    /** COALESCE(per-warehouse override, product minimum) for the row being evaluated. */
    private static Expression<BigDecimal> effectiveMinimum(
            Root<InventoryStockSnapshotEntity> root,
            jakarta.persistence.criteria.CommonAbstractCriteria query,
            CriteriaBuilder cb) {

        Subquery<BigDecimal> override = query.subquery(BigDecimal.class);
        Root<ProductWarehouseMinimumStockEntity> overrideRoot =
                override.from(ProductWarehouseMinimumStockEntity.class);

        override.select(overrideRoot.get("minimumStock"))
                .where(
                        cb.equal(overrideRoot.get("product"), root.get("product")),
                        cb.equal(overrideRoot.get("warehouse"), root.get("warehouse"))
                );

        CriteriaBuilder.Coalesce<BigDecimal> coalesce = cb.coalesce();
        coalesce.value(override);
        coalesce.value(root.<ProductEntity>get("product").<BigDecimal>get("minimumStock"));

        return coalesce;
    }

    public static Specification<InventoryStockSnapshotEntity> fromFilter(StockPageFilter filter) {
        if (filter == null) {
            return Specification.where(null);
        }

        return Specification
                .where(matchesSearch(filter.search()))
                .and(hasWarehouseId(filter.warehouseId()))
                .and(hasStatus(filter.status()));
    }

    /**
     * Everything except the status filter — the scope the low-stock badge is counted over.
     * Applying the status filter there would make the badge echo the paginator whenever
     * the user filters by "below minimum".
     */
    public static Specification<InventoryStockSnapshotEntity> fromFilterIgnoringStatus(
            StockPageFilter filter) {

        if (filter == null) {
            return Specification.where(null);
        }

        return Specification
                .where(matchesSearch(filter.search()))
                .and(hasWarehouseId(filter.warehouseId()))
                .and(hasStatus(StockStatusFilter.BELOW_MINIMUM));
    }
}
