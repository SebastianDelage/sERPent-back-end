-- V21__add_product_warehouse_minimum_stock.sql
-- Per-warehouse minimum stock: an EXCEPTION to products.minimum_stock, not a replacement.
--
-- Low stock is decided per warehouse, and the threshold resolves in cascade: the row for
-- that (product, warehouse) if one exists, otherwise the product's own minimum_stock. A
-- product with no minimum at either level is never "low" and stays out of the report —
-- there are goods nobody wants to track, and that is the pre-existing behaviour.
--
-- The table starts empty on purpose: with no rows, every product falls back to its own
-- minimum and the system behaves exactly as it did before, only compared per warehouse
-- instead of against the summed total.
--
-- One row per (product, warehouse), enforced by the unique constraint. No `active` flag
-- here, unlike the rest of the catalog: an override that should stop applying is simply
-- deleted, and the product-level minimum takes over again. There is no history to keep.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V11__add_product_warehouse_minimum_stock.sql

CREATE TABLE product_warehouse_minimum_stock (
                                                 product_warehouse_minimum_stock_id BIGSERIAL PRIMARY KEY,
                                                 product_id    BIGINT        NOT NULL,
                                                 warehouse_id  BIGINT        NOT NULL,
                                                 minimum_stock NUMERIC(12,3) NOT NULL,

                                                 CONSTRAINT fk_pwms_product
                                                     FOREIGN KEY (product_id) REFERENCES products(product_id)
                                                         ON DELETE CASCADE,

                                                 CONSTRAINT fk_pwms_warehouse
                                                     FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),

                                                 CONSTRAINT ux_pwms_product_warehouse
                                                     UNIQUE (product_id, warehouse_id),

                                                 CONSTRAINT ck_pwms_minimum_non_negative
                                                     CHECK (minimum_stock >= 0)
);

CREATE INDEX idx_pwms_warehouse ON product_warehouse_minimum_stock(warehouse_id);
