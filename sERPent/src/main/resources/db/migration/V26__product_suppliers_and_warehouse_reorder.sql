-- V26__product_suppliers_and_warehouse_reorder.sql
-- Two changes that only make sense together: the replenishment report needs to say WHO to
-- buy from, and it needs to fire at the right moment PER BRANCH.
--
-- 1) product_suppliers stops storing a price and starts storing the supplier's own code.
-- 2) the per-warehouse override table widens from "minimum stock" to the whole reorder
--    triplet, so a branch that sells three times as much can order earlier, not just hold
--    a higher floor.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V16__product_suppliers_and_warehouse_reorder.sql

-- =========================
-- 1) PRODUCT SUPPLIERS
-- =========================
-- The supplier's own code for our product. NOT our SKU: it is what appears on their price
-- list and their invoice, and it is what you quote back at them when ordering. Optional,
-- because plenty of small suppliers do not use one.
ALTER TABLE product_suppliers
    ADD COLUMN supplier_product_code VARCHAR(80);

-- cost_price goes away, and this is the point of the change rather than a side effect.
-- What we last paid is already recorded, exactly and with its date, on the purchase lines
-- (transaction_details of a PURCHASE). Keeping a second copy here means two numbers that
-- answer the same question and drift apart the first time someone loads a purchase without
-- coming back to update the catalog. The report derives it from the purchases instead.
--
-- Safe to drop: the column has never been read. It was written only by the dev seed, and
-- ProductSupplierEntity was the sole reference to it in the whole codebase.
ALTER TABLE product_suppliers
    DROP CONSTRAINT ck_product_suppliers_cost_nonneg;

ALTER TABLE product_suppliers
    DROP COLUMN cost_price;

-- =========================
-- 2) PER-WAREHOUSE REORDER OVERRIDES
-- =========================
-- The table was born holding one figure; it now holds three. The physical name stays
-- product_warehouse_minimum_stock so that no existing index, constraint or foreign key
-- has to be rewritten — renaming it would churn every reference for a cosmetic gain. The
-- entity javadoc says out loud that the name is narrower than the content.
--
-- WHY minimum_stock BECOMES NULLABLE: the three figures cascade INDEPENDENTLY. A branch
-- that only wants to order earlier — same floor, sooner trigger — must be able to set
-- reorder_point alone. With minimum_stock still NOT NULL it would have to copy the
-- product's minimum into the row, and that copy would silently go stale the day the
-- product's minimum changes. Exactly the two-sources-of-truth problem this same migration
-- removes from product_suppliers, so it is not introduced here.
--
-- A row with all three NULL means nothing at all, and is rejected: the service deletes the
-- row instead, which is the same thing said properly — inherit everything.
ALTER TABLE product_warehouse_minimum_stock
    ALTER COLUMN minimum_stock DROP NOT NULL;

ALTER TABLE product_warehouse_minimum_stock
    ADD COLUMN reorder_point NUMERIC(12,3);

ALTER TABLE product_warehouse_minimum_stock
    ADD COLUMN reorder_quantity NUMERIC(12,3);

ALTER TABLE product_warehouse_minimum_stock
    ADD CONSTRAINT ck_pwms_reorder_point_non_negative
        CHECK (reorder_point IS NULL OR reorder_point >= 0);

ALTER TABLE product_warehouse_minimum_stock
    ADD CONSTRAINT ck_pwms_reorder_quantity_non_negative
        CHECK (reorder_quantity IS NULL OR reorder_quantity >= 0);

-- An override that overrides nothing is not a state the app should be able to reach.
ALTER TABLE product_warehouse_minimum_stock
    ADD CONSTRAINT ck_pwms_at_least_one_value
        CHECK (minimum_stock IS NOT NULL
               OR reorder_point IS NOT NULL
               OR reorder_quantity IS NOT NULL);

-- NOT enforced here: "reorder point >= minimum stock" resolved through the cascade. It
-- cannot be a CHECK because each side may come from a different place — the row for one
-- and the product for the other — and a row constraint cannot see the product. It lives in
-- ProductWarehouseMinimumStockService, which validates the RESOLVED pair, and in
-- ProductService, which re-checks every override before letting the product's own figures
-- move underneath them.
