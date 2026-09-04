package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.reports.repository.projection.InventoryMovementsByProductProjection;
import com.empresa.serpent.reports.repository.projection.InventoryMovementsByTypeProjection;
import com.empresa.serpent.reports.repository.projection.InventoryMovementsByWarehouseProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.empresa.serpent.transactions.repository.projection.TransactionWarehouseProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryMovementRepository extends
        JpaRepository<InventoryMovementEntity, Long>,
        JpaSpecificationExecutor<InventoryMovementEntity> {

    /**
     * The movements listing, with everything the row needs already loaded.
     *
     * <p>OVERRIDDEN ONLY TO ATTACH THE GRAPH. Without it this was one query plus four lazy
     * SELECTs per row — product, warehouse, counterpart warehouse and transaction are all
     * {@code FetchType.LAZY} and the response mapper navigates every one of them. A page of
     * ten cost up to 41 queries; a page of fifty, up to 201.
     *
     * <p>Safe to join-fetch alongside pagination because all four are {@code @ManyToOne}: one
     * row in, one row out, so the page window still means what it says. The pitfall that makes
     * Hibernate paginate in memory is a fetched COLLECTION, and there is none here.
     *
     * <p>Only the listing goes through this overload; the snapshot service uses the no-arg
     * {@code findAll()} and is untouched.
     */
    @Override
    @EntityGraph(attributePaths = {"product", "warehouse", "counterpartWarehouse", "transaction"})
    Page<InventoryMovementEntity> findAll(Specification<InventoryMovementEntity> spec, Pageable pageable);

    List<InventoryMovementEntity> findByProductId(Long productId);

    List<InventoryMovementEntity> findByWarehouseId(Long warehouseId);

    List<InventoryMovementEntity> findByWarehouseIdIn(List<Long> warehouseIds);

    /**
     * The branches each of these transactions touched, in one query.
     *
     * <p>Feeds the history listing's branch column. DISTINCT because a transaction writes
     * one movement per line and they nearly always share a warehouse; a transfer is the
     * case that legitimately yields two.
     */
    @Query("""
           SELECT DISTINCT m.transaction.id AS transactionId, w.name AS warehouseName
           FROM InventoryMovementEntity m
           JOIN m.warehouse w
           WHERE m.transaction.id IN :transactionIds
           ORDER BY w.name
           """)
    List<TransactionWarehouseProjection> findWarehouseNamesByTransactionIds(
            @Param("transactionIds") List<Long> transactionIds);

    List<InventoryMovementEntity> findByTransactionId(Long transactionId);

    List<InventoryMovementEntity> findByMovementType(MovementType movementType);

    List<InventoryMovementEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<InventoryMovementEntity> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Query("""
       SELECT
           m.movementType AS movementType,
           COUNT(m.id) AS movements,
           COALESCE(SUM(m.quantity), 0) AS totalQuantity
       FROM InventoryMovementEntity m
       WHERE (:unrestricted = TRUE OR m.warehouse.id IN :warehouseIds)
       GROUP BY m.movementType
       ORDER BY m.movementType
       """)
    List<InventoryMovementsByTypeProjection> getInventoryMovementsByTypeReportRaw(
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
    );

    @Query("""
       SELECT
           m.warehouse.id AS warehouseId,
           m.warehouse.name AS warehouseName,
           COUNT(m.id) AS movements,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalIn,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_OUT
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalOut,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE -m.quantity
               END
           ), 0) AS netQuantity
       FROM InventoryMovementEntity m
       GROUP BY m.warehouse.id, m.warehouse.name
       ORDER BY m.warehouse.name
       """)
    List<InventoryMovementsByWarehouseProjection> getInventoryMovementsByWarehouseReportRaw();

    @Query("""
       SELECT
           m.product.id AS productId,
           m.product.name AS productName,
           COUNT(m.id) AS movements,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalIn,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_OUT
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalOut,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE -m.quantity
               END
           ), 0) AS netQuantity
       FROM InventoryMovementEntity m
       WHERE (:unrestricted = TRUE OR m.warehouse.id IN :warehouseIds)
       GROUP BY m.product.id, m.product.name
       ORDER BY m.product.name
       """)
    List<InventoryMovementsByProductProjection> getInventoryMovementsByProductReportRaw(
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
    );
}