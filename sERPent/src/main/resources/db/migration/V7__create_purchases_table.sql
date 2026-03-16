CREATE TABLE purchases (
                           purchase_id BIGSERIAL PRIMARY KEY,
                           transaction_id BIGINT NOT NULL,
                           supplier_id BIGINT,
                           warehouse_id BIGINT NOT NULL,
                           receipt_number VARCHAR(80),
                           notes TEXT,

                           CONSTRAINT fk_purchases_transaction
                               FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                           CONSTRAINT fk_purchases_supplier
                               FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),

                           CONSTRAINT fk_purchases_warehouse
                               FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),

                           CONSTRAINT ux_purchases_transaction UNIQUE (transaction_id),
                           CONSTRAINT ux_purchases_receipt_number UNIQUE (receipt_number)
);

CREATE INDEX idx_purchases_supplier
    ON purchases(supplier_id);

CREATE INDEX idx_purchases_warehouse
    ON purchases(warehouse_id);