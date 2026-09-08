package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.reports.repository.projection.InventoryReplenishmentProjection;
import com.empresa.serpent.reports.repository.projection.ProductStockProjection;
import com.empresa.serpent.reports.repository.projection.StockRowProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryStockSnapshotRepository extends
        JpaRepository<InventoryStockSnapshotEntity, Long>,
        JpaSpecificationExecutor<InventoryStockSnapshotEntity> {

    Optional<InventoryStockSnapshotEntity> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<InventoryStockSnapshotEntity> findByProductId(Long productId);

    List<InventoryStockSnapshotEntity> findByWarehouseId(Long warehouseId);

    List<InventoryStockSnapshotEntity> findByWarehouseIdIn(List<Long> warehouseIds);

    List<InventoryStockSnapshotEntity> findByProductIdAndWarehouseIdIn(Long productId, List<Long> warehouseIds);

    /**
     * The per-product view, paginated: one row per product with its stock summed across
     * warehouses.
     *
     * <p>Specifications cannot serve this one — {@code JpaSpecificationExecutor} pages
     * entities, and this pages GROUPS. Hence an explicit aggregate query with its own
     * {@code countQuery}, which counts the distinct products rather than the snapshot
     * rows behind them.
     *
     * <p>{@code belowMinimum} shows a product when it is short in AT LEAST ONE warehouse,
     * which is the product-level reading of a per-warehouse condition. The cascade is the
     * same COALESCE used everywhere else, and the explicit IS NOT NULL keeps products
     * with no minimum at either level out rather than leaning on NULL comparison
     * semantics.
     *
     * <p>The status conditions are EXISTS subqueries rather than predicates on the rows
     * being summed, and that distinction matters: the status decides WHICH PRODUCTS
     * qualify, while the total stays the sum across their warehouses. Filtering the summed
     * rows instead would report "Pollo entero: 8" under a "Stock total" heading for a
     * product that actually holds 37 — the filter would silently rewrite the number it is
     * supposed to be selecting on.
     */
    @Query(value = """
           SELECT p.id AS productId,
                  p.name AS productName,
                  COALESCE(SUM(s.currentStock), 0) AS totalStock
           FROM InventoryStockSnapshotEntity s
           JOIN s.product p
           WHERE (:search IS NULL
                  OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(p.sku) = LOWER(:search)
                  OR p.barcode = :search)
             AND (:unrestricted = TRUE OR s.warehouse.id IN :warehouseIds)
             AND (:outOfStock = FALSE OR EXISTS (
                     SELECT 1 FROM InventoryStockSnapshotEntity s2
                      WHERE s2.product = p
                        AND (:unrestricted = TRUE OR s2.warehouse.id IN :warehouseIds)
                        AND s2.currentStock <= 0))
             AND (:inStock = FALSE OR EXISTS (
                     SELECT 1 FROM InventoryStockSnapshotEntity s2
                      WHERE s2.product = p
                        AND (:unrestricted = TRUE OR s2.warehouse.id IN :warehouseIds)
                        AND s2.currentStock > 0))
             AND (:belowMinimum = FALSE OR EXISTS (
                     SELECT 1 FROM InventoryStockSnapshotEntity s2
                      WHERE s2.product = p
                        AND (:unrestricted = TRUE OR s2.warehouse.id IN :warehouseIds)
                        AND COALESCE(
                                (SELECT m.minimumStock
                                   FROM ProductWarehouseMinimumStockEntity m
                                  WHERE m.product = p AND m.warehouse = s2.warehouse),
                                p.minimumStock
                            ) IS NOT NULL
                        AND s2.currentStock <= COALESCE(
                                (SELECT m.minimumStock
                                   FROM ProductWarehouseMinimumStockEntity m
                                  WHERE m.product = p AND m.warehouse = s2.warehouse),
                                p.minimumStock
                            )))
           GROUP BY p.id, p.name
           ORDER BY p.name
           """,
            countQuery = """
           SELECT COUNT(DISTINCT p.id)
           FROM InventoryStockSnapshotEntity s
           JOIN s.product p
           WHERE (:search IS NULL
                  OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(p.sku) = LOWER(:search)
                  OR p.barcode = :search)
             AND (:unrestricted = TRUE OR s.warehouse.id IN :warehouseIds)
             AND (:outOfStock = FALSE OR EXISTS (
                     SELECT 1 FROM InventoryStockSnapshotEntity s2
                      WHERE s2.product = p
                        AND (:unrestricted = TRUE OR s2.warehouse.id IN :warehouseIds)
                        AND s2.currentStock <= 0))
             AND (:inStock = FALSE OR EXISTS (
                     SELECT 1 FROM InventoryStockSnapshotEntity s2
                      WHERE s2.product = p
                        AND (:unrestricted = TRUE OR s2.warehouse.id IN :warehouseIds)
                        AND s2.currentStock > 0))
             AND (:belowMinimum = FALSE OR EXISTS (
                     SELECT 1 FROM InventoryStockSnapshotEntity s2
                      WHERE s2.product = p
                        AND (:unrestricted = TRUE OR s2.warehouse.id IN :warehouseIds)
                        AND COALESCE(
                                (SELECT m.minimumStock
                                   FROM ProductWarehouseMinimumStockEntity m
                                  WHERE m.product = p AND m.warehouse = s2.warehouse),
                                p.minimumStock
                            ) IS NOT NULL
                        AND s2.currentStock <= COALESCE(
                                (SELECT m.minimumStock
                                   FROM ProductWarehouseMinimumStockEntity m
                                  WHERE m.product = p AND m.warehouse = s2.warehouse),
                                p.minimumStock
                            )))
           """)
    Page<ProductStockProjection> searchGroupedByProduct(
            @Param("search") String search,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("outOfStock") boolean outOfStock,
            @Param("inStock") boolean inStock,
            @Param("belowMinimum") boolean belowMinimum,
            Pageable pageable
    );

    /**
     * Las filas de stock que la pantalla necesita, en UNA consulta y sin entidades gestionadas.
     *
     * <p>Reemplaza a los cuatro finders de arriba en el camino de {@code getStock}: los cuatro
     * bajaban entidades y el servicio después leía el nombre del producto y el del depósito,
     * que son relaciones perezosas. Diez filas costaban doce sentencias; con esto son dos.
     *
     * <p>LOS CUATRO CASOS EN UNA SOLA CONSULTA. El servicio entraba por un finder distinto
     * según hubiera o no producto y según el alcance por depósito. Acá los dos ejes son
     * parámetros, con el mismo idioma que ya usa {@code searchGroupedByProduct}:
     * {@code :unrestricted = TRUE} cortocircuita el filtro de depósito, y la lista vacía que
     * trae WarehouseScope cuando no hay restricción nunca se evalúa.
     *
     * <p>NO FILTRA POR {@code onlyPositive} NI ORDENA. Las dos cosas se quedan en el servicio,
     * donde ya estaban, y es deliberado: ordenar en SQL cambiaría el criterio de comparación
     * —{@code compareToIgnoreCase} de Java contra la colación de la base— y eso reordena una
     * lista que el operador mira, con eñes y acentos de por medio. Lo único que este cambio
     * mueve es cuántas sentencias se mandan.
     *
     * <p>Los finders que devuelven entidades se dejan: los usan la reconstrucción de snapshots
     * y la reconciliación, que sí necesitan la entidad.
     */
    @Query("""
           SELECT p.id AS productId,
                  p.name AS productName,
                  w.id AS warehouseId,
                  w.name AS warehouseName,
                  s.currentStock AS currentStock,
                  w.active AS warehouseActive
           FROM InventoryStockSnapshotEntity s
           JOIN s.product p
           JOIN s.warehouse w
           WHERE (:productId IS NULL OR p.id = :productId)
             AND (:unrestricted = TRUE OR w.id IN :warehouseIds)
           """)
    List<StockRowProjection> findStockRows(
            @Param("productId") Long productId,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
    );

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

    /**
     * What has fallen to its reorder point, per warehouse.
     *
     * <p>THE TRIGGER IS THE REORDER POINT, NOT THE MINIMUM, and the two are not
     * interchangeable: the minimum is the floor you do not want to break through, the
     * reorder point is when you order so the goods arrive before you touch it. Firing on the
     * minimum would warn once it is already too late.
     *
     * <p>All three figures resolve through the SAME cascade the low-stock queries use — the
     * warehouse's override when it has one, the product's otherwise — so the stock screen
     * and this report finally speak about the same product at the same branch on the same
     * terms. The explicit IS NOT NULL keeps products with no reorder point at either level
     * out, rather than leaning on NULL comparison semantics.
     *
     * <p>The subquery is repeated instead of hoisted because JPQL has no LATERAL: each
     * COALESCE needs the override for THIS row's warehouse, and the (product, warehouse)
     * unique constraint makes every one of them return at most one row.
     */
    @Query("""
       SELECT
           p.id AS productId,
           p.name AS productName,
           p.sku AS productSku,
           w.id AS warehouseId,
           w.name AS warehouseName,
           s.currentStock AS currentStock,
           COALESCE(
               (SELECT m.minimumStock FROM ProductWarehouseMinimumStockEntity m
                 WHERE m.product = p AND m.warehouse = w),
               p.minimumStock
           ) AS minimumStock,
           COALESCE(
               (SELECT m.reorderPoint FROM ProductWarehouseMinimumStockEntity m
                 WHERE m.product = p AND m.warehouse = w),
               p.reorderPoint
           ) AS reorderPoint,
           COALESCE(
               (SELECT m.reorderQuantity FROM ProductWarehouseMinimumStockEntity m
                 WHERE m.product = p AND m.warehouse = w),
               p.reorderQuantity
           ) AS reorderQuantity
       FROM InventoryStockSnapshotEntity s
       JOIN s.product p
       JOIN s.warehouse w
       WHERE (:unrestricted = TRUE OR w.id IN :warehouseIds)
         AND (:warehouseId IS NULL OR w.id = :warehouseId)
         AND COALESCE(
                 (SELECT m.reorderPoint FROM ProductWarehouseMinimumStockEntity m
                   WHERE m.product = p AND m.warehouse = w),
                 p.reorderPoint
             ) IS NOT NULL
         AND s.currentStock <= COALESCE(
                 (SELECT m.reorderPoint FROM ProductWarehouseMinimumStockEntity m
                   WHERE m.product = p AND m.warehouse = w),
                 p.reorderPoint
             )
       ORDER BY p.name, w.name
       """)
    List<InventoryReplenishmentProjection> getReplenishmentReportRaw(
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("warehouseId") Long warehouseId
    );
}