CREATE TABLE inventory_stock_snapshot (
                                          snapshot_id BIGSERIAL PRIMARY KEY,
                                          product_id BIGINT NOT NULL,
                                          warehouse_id BIGINT NOT NULL,
                                          current_stock NUMERIC(12,3) NOT NULL DEFAULT 0,
                                          updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                          last_movement_id BIGINT,

                                          CONSTRAINT fk_inventory_stock_snapshot_product
                                              FOREIGN KEY (product_id) REFERENCES products(product_id),

                                          CONSTRAINT fk_inventory_stock_snapshot_warehouse
                                              FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),

                                          CONSTRAINT fk_inventory_stock_snapshot_last_movement
                                              FOREIGN KEY (last_movement_id) REFERENCES inventory_movements(movement_id),

                                          CONSTRAINT ux_inventory_stock_snapshot_product_warehouse
                                              UNIQUE (product_id, warehouse_id)
);

CREATE INDEX idx_inventory_stock_snapshot_product
    ON inventory_stock_snapshot(product_id);

CREATE INDEX idx_inventory_stock_snapshot_warehouse
    ON inventory_stock_snapshot(warehouse_id);

CREATE INDEX idx_inventory_stock_snapshot_current_stock
    ON inventory_stock_snapshot(current_stock);