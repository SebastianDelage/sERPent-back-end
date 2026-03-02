package com.empresa.serpent.inventory.domain;

import com.empresa.serpent.transactions.domain.entity.Transaction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Date;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "inventory_movements")
public class InvetoryMovements {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_movements_id",nullable = false,updatable = false)
    private Long id;

    @NotEmpty
    @Column(name = "movement_type")
    private String movementType;
    @NotNull
    @Column(name = "quantity",length = 100,precision = 12,scale = 2)
    private Double quantity;
    @NotNull
    @Column(name = "unit_cost",length = 100,precision = 12,scale = 2)
    private Double unitCost;
    @NotNull
    @Column(name = "crated_at")
    private Date cratedAt;
    @NotEmpty
    @Column(name = "note")
    private String note;
    @ManyToOne
    @JoinColumn(name="transaction_id",nullable = false)
    private Transaction transaction;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

}
