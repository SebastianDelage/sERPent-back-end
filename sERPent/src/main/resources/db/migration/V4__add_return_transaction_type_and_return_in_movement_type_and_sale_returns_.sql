-- V4__add_transfer_return_and_sale_returns.sql
-- PostgreSQL migration for:
-- - Transaction type TRANSFER
-- - Transaction type RETURN
-- - Sale returns table
-- - Inventory movement type RETURN_IN

-- =========================
-- 1) UPDATE TRANSACTIONS TYPE CONSTRAINT
-- =========================
ALTER TABLE transactions
DROP CONSTRAINT ck_transactions_type;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type
        CHECK (type IN ('SALE', 'EXPENSE', 'PURCHASE', 'ADJUSTMENT', 'TRANSFER', 'RETURN'));

-- =========================
-- 2) CREATE SALE_RETURNS TABLE
-- =========================
CREATE TABLE sale_returns (
                              sale_return_id BIGSERIAL PRIMARY KEY,
                              transaction_id BIGINT NOT NULL,
                              original_sale_id BIGINT NOT NULL,
                              reason TEXT,

                              CONSTRAINT fk_sale_returns_transaction
                                  FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                              CONSTRAINT fk_sale_returns_original_sale
                                  FOREIGN KEY (original_sale_id) REFERENCES sales(sale_id),

                              CONSTRAINT ux_sale_returns_transaction UNIQUE (transaction_id)
);

CREATE INDEX idx_sale_returns_original_sale
    ON sale_returns(original_sale_id);

-- =========================
-- 3) UPDATE INVENTORY MOVEMENTS TYPE CONSTRAINT
-- =========================
ALTER TABLE inventory_movements
DROP CONSTRAINT ck_inventory_movements_type;

ALTER TABLE inventory_movements
    ADD CONSTRAINT ck_inventory_movements_type
        CHECK (
            movement_type IN (
                              'IN',
                              'OUT',
                              'ADJUSTMENT_IN',
                              'ADJUSTMENT_OUT',
                              'TRANSFER_IN',
                              'TRANSFER_OUT',
                              'RETURN_IN'
                )
            );