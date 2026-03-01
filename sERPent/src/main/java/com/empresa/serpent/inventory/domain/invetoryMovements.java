package com.empresa.serpent.inventory.domain;

import com.empresa.serpent.transactions.domain.entity.Transaction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

@Entity
@Table(name = "inventory_movements")
public class invetoryMovements {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invetoryMovementsId;

    @NotEmpty
    private String movementType;
    @NotNull
    private Double quantity;
    @NotNull
    private Double unitCost;
    @NotNull
    private Date cratedAt;
    @NotEmpty
    private String note;





    @ManyToOne
    @JoinColumn(name="transaction_id",nullable = false)
    private Transaction transaction;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private warehouse warehouse;

    public invetoryMovements() {
    }

    public Long getInvetoryMovementsId() {
        return invetoryMovementsId;
    }

    public void setInvetoryMovementsId(Long invetoryMovementsId) {
        this.invetoryMovementsId = invetoryMovementsId;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(Double unitCost) {
        this.unitCost = unitCost;
    }

    public Date getCratedAt() {
        return cratedAt;
    }

    public void setCratedAt(Date cratedAt) {
        this.cratedAt = cratedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(warehouse warehouse) {
        this.warehouse = warehouse;
    }
}
