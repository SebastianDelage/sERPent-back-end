package com.empresa.serpent.inventory.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "inventory_stock_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_inventory_stock_snapshot_product_warehouse",
                        columnNames = {"product_id", "warehouse_id"}
                )
        }
)
public class InventoryStockSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    @Column(name = "current_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal currentStock;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * El movimiento que dejó este saldo como está, por id y NO como relación.
     *
     * <h2>LA BASE SÍ TIENE LA CLAVE FORÁNEA</h2>
     *
     * <p>Se llama {@code fk_inventory_stock_snapshot_last_movement} y apunta a
     * {@code inventory_movements(movement_id)}. Que acá haya un Long suelto no quiere decir que
     * la referencia sea libre: la integridad la garantiza la base, y hoy el suite la ejerce
     * porque corre contra las migraciones de verdad con {@code ddl-auto=validate}.
     *
     * <p>Esto estuvo invisible un tiempo y conviene que quede escrito: mientras el suite armaba
     * el esquema desde las entidades, Hibernate no creaba esa FK —no la conoce— y tres tests
     * escribían ids de movimiento inventados sin que nadie los rechazara.
     *
     * <h2>POR QUÉ NO SE MAPEA COMO @ManyToOne</h2>
     *
     * <p>Porque <b>nada la navega</b>. Ningún código de producción llama a
     * {@code getLastMovementId()}: la columna se escribe y no se lee. Y se escribe casi siempre
     * por fuera de esta entidad, en las cuatro consultas nativas del repositorio
     * —{@code increaseStock}, las dos de bajada y {@code insertZeroSnapshot}—, que le pasan el
     * id como parámetro y no ven el modelo. El único lugar que la escribe por acá es
     * {@code InventoryStockSnapshotService.rebuildSnapshots}, que ya tiene el movimiento en la
     * mano y le pide el id.
     *
     * <p>Mapearla no sería gratis. Un {@code @ManyToOne} es EAGER por defecto, y esta entidad se
     * lee entera —todas las filas de un depósito— cada vez que se abre la pantalla de venta y
     * después de cada venta confirmada. MEDIDO en StockSnapshotQueryCountTest, agregando la
     * relación EAGER y volviendo a contar: con tres filas la lectura pasa de <b>5 sentencias a
     * 8</b>, o sea una más por fila, para traer un movimiento que nadie mira.
     *
     * <p>Con LAZY el costo de lectura no cambia, pero aparecería una asociación navegable que
     * nadie necesita, justo sobre la consulta más caliente de la app.
     * Alcanza con que alguien escriba {@code snapshot.getLastMovement().getCreatedAt()} adentro
     * de ese recorrido para volver a tener una consulta por fila. Hoy eso es imposible de
     * escribir, y esa imposibilidad es la que se está eligiendo conservar.
     *
     * <p>Si algún día hace falta navegarlo, el argumento se da vuelta y hay que mapearlo con
     * {@code fetch = LAZY} explícito. La condición para revisar esta decisión es exactamente
     * esa: que aparezca el primer lector.
     */
    @Column(name = "last_movement_id")
    private Long lastMovementId;
}