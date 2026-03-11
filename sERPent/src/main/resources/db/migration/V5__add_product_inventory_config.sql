ALTER TABLE products
    ADD COLUMN minimum_stock NUMERIC(12,3);

ALTER TABLE products
    ADD COLUMN reorder_point NUMERIC(12,3);

ALTER TABLE products
    ADD COLUMN reorder_quantity NUMERIC(12,3);