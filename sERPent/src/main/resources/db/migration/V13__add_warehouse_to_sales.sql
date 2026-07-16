ALTER TABLE sales
    ADD COLUMN warehouse_id BIGINT;

ALTER TABLE sales
    ADD CONSTRAINT fk_sales_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id);