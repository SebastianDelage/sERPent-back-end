package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.reports.repository.projection.InventoryReplenishmentProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryStockSnapshotRepository extends JpaRepository<InventoryStockSnapshotEntity, Long> {

    Optional<InventoryStockSnapshotEntity> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<InventoryStockSnapshotEntity> findByProductId(Long productId);

    List<InventoryStockSnapshotEntity> findByWarehouseId(Long warehouseId);

    /**
     * Atomically adds {@code quantity} to the balance of an existing snapshot row.
     * Used for every stock increase (IN, ADJUSTMENT_IN, TRANSFER_IN, RETURN_IN).
     * Returns the number of affected rows (0 when the row does not exist yet).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_stock_snapshot
               SET current_stock = current_stock + :quantity,
                   updated_at = CURRENT_TIMESTAMP,
                   last_movement_id = :movementId
             WHERE product_id = :productId
               AND warehouse_id = :warehouseId
            """, nativeQuery = true)
    int increaseStock(@Param("productId") Long productId,
                      @Param("warehouseId") Long warehouseId,
                      @Param("quantity") BigDecimal quantity,
                      @Param("movementId") Long movementId);

    /**
     * Atomically subtracts {@code quantity} from the balance ONLY when there is enough stock
     * ({@code current_stock >= quantity}). This conditional UPDATE is the real oversell guard for
     * vendible outputs (OUT, TRANSFER_OUT): concurrent operations cannot both succeed past the
     * floor. Returns the number of affected rows (0 when stock is insufficient or the row is
     * missing).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_stock_snapshot
               SET current_stock = current_stock - :quantity,
                   updated_at = CURRENT_TIMESTAMP,
                   last_movement_id = :movementId
             WHERE product_id = :productId
               AND warehouse_id = :warehouseId
               AND current_stock >= :quantity
            """, nativeQuery = true)
    int decreaseStockWithFloor(@Param("productId") Long productId,
                               @Param("warehouseId") Long warehouseId,
                               @Param("quantity") BigDecimal quantity,
                               @Param("movementId") Long movementId);

    /**
     * Atomically subtracts {@code quantity} from the balance with no floor. Used for
     * ADJUSTMENT_OUT only: a physical recount may legitimately lower stock (even to zero).
     * Returns the number of affected rows (0 when the row does not exist yet).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_stock_snapshot
               SET current_stock = current_stock - :quantity,
                   updated_at = CURRENT_TIMESTAMP,
                   last_movement_id = :movementId
             WHERE product_id = :productId
               AND warehouse_id = :warehouseId
            """, nativeQuery = true)
    int decreaseStockWithoutFloor(@Param("productId") Long productId,
                                  @Param("warehouseId") Long warehouseId,
                                  @Param("quantity") BigDecimal quantity,
                                  @Param("movementId") Long movementId);

    /**
     * Inserts a fresh zero-balance snapshot row. A concurrent insert for the same
     * product+warehouse collides against ux_inventory_stock_snapshot_product_warehouse and raises
     * a DataIntegrityViolationException, which the caller resolves at the REQUIRES_NEW boundary.
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_stock_snapshot
                (product_id, warehouse_id, current_stock, updated_at, last_movement_id)
            VALUES (:productId, :warehouseId, 0, CURRENT_TIMESTAMP, NULL)
            """, nativeQuery = true)
    void insertZeroSnapshot(@Param("productId") Long productId,
                            @Param("warehouseId") Long warehouseId);

    @Query("""
       SELECT
           p.id AS productId,
           p.name AS productName,
           w.id AS warehouseId,
           w.name AS warehouseName,
           s.currentStock AS currentStock,
           p.reorderPoint AS reorderPoint,
           p.reorderQuantity AS reorderQuantity
       FROM InventoryStockSnapshotEntity s
       JOIN s.product p
       JOIN s.warehouse w
       WHERE p.reorderPoint IS NOT NULL
         AND s.currentStock <= p.reorderPoint
       ORDER BY p.name
       """)
    List<InventoryReplenishmentProjection> getReplenishmentReportRaw();
}