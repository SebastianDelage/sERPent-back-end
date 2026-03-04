-- V2__add_transaction_detail_to_inventory_movements.sql
-- Link inventory movements to the exact transaction line (ERP-grade traceability)

ALTER TABLE inventory_movements
    ADD COLUMN transaction_detail_id BIGINT;

ALTER TABLE inventory_movements
    ADD CONSTRAINT fk_inventory_movements_transaction_detail
        FOREIGN KEY (transaction_detail_id)
            REFERENCES transaction_details(transaction_detail_id);

-- Useful indexes for audits & stock tracing
CREATE INDEX idx_inventory_movements_transaction_detail
    ON inventory_movements(transaction_detail_id);
