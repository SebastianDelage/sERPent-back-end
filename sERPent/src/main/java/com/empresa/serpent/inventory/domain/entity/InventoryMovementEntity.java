package com.empresa.serpent.inventory.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @NotNull
    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Lo que ESCRIBIÓ EL OPERADOR, y nada más.
     *
     * <p>Antes acá también se guardaba texto generado —"Sale #9", "Transfer #9 from
     * warehouse 1", "Conteo: 9999.999, anterior: 12.530"— y eso era el bug: quedaba
     * congelado en inglés y con punto decimal, y arreglar el código no tocaba lo ya
     * registrado. El origen ahora se compone al mostrar, a partir de los campos de abajo.
     *
     * <p>El motivo que tipea una persona sí es un dato y no se puede componer, así que se
     * queda. Puede ser null.
     */
    @Column(name = "note")
    private String note;

    /**
     * Lo que la persona contó, en un ajuste de inventario. Null en todo lo demás.
     *
     * <p>Junto con {@code previousQuantity} reemplaza a la frase que se guardaba en
     * {@code note}. Las filas anteriores a esta columna quedan en null a propósito: los dos
     * números existen dentro del texto viejo, pero ese texto es ambiguo por el mismo bug que
     * esto corrige, y desambiguarlo a mano sería convertir una lectura dudosa en un dato que
     * después nadie vuelve a cuestionar.
     */
    @Column(name = "counted_quantity", precision = 12, scale = 3)
    private BigDecimal countedQuantity;

    /** Lo que el sistema tenía antes del ajuste. Null en todo lo que no sea un ajuste. */
    @Column(name = "previous_quantity", precision = 12, scale = 3)
    private BigDecimal previousQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    /**
     * El depósito del OTRO lado de una transferencia. Null en todo lo demás.
     *
     * <p>Una transferencia deja dos movimientos, el OUT en el origen y el IN en el destino,
     * y cada uno conoce su propio depósito por {@link #warehouse} pero no el del hermano —
     * que es justo el que el operador necesita leer. No se puede derivar de la fila sola, así
     * que se guarda: un renglón de auditoría tiene que explicarse solo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterpart_warehouse_id")
    private WarehouseEntity counterpartWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;
}
