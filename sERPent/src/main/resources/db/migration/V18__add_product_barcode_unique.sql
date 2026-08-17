-- V18__add_product_barcode_unique.sql
-- Barcode becomes unique when present. Nullable + UNIQUE lets Postgres allow
-- any number of NULLs (most products have no barcode), same pattern as sku.

ALTER TABLE products
    ADD CONSTRAINT ux_products_barcode UNIQUE (barcode);
