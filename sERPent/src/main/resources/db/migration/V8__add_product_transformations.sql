-- V5__add_product_transformations.sql
-- PostgreSQL migration for:
-- - Transaction type TRANSFORMATION
-- - Product transformations
-- - Transformation inputs
-- - Transformation outputs

-- =========================
-- 1) UPDATE TRANSACTIONS TYPE CONSTRAINT
-- =========================
ALTER TABLE transactions
DROP CONSTRAINT ck_transactions_type;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type
        CHECK (
            type IN (
                     'SALE',
                     'EXPENSE',
                     'PURCHASE',
                     'ADJUSTMENT',
                     'TRANSFER',
                     'RETURN',
                     'TRANSFORMATION'
                )
            );

-- =========================
-- 2) CREATE PRODUCT_TRANSFORMATIONS TABLE
-- =========================
CREATE TABLE product_transformations (
                                         product_transformation_id BIGSERIAL PRIMARY KEY,
                                         transaction_id BIGINT NOT NULL,
                                         warehouse_id BIGINT NOT NULL,
                                         notes TEXT,

                                         CONSTRAINT fk_product_transformations_transaction
                                             FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                                         CONSTRAINT fk_product_transformations_warehouse
                                             FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),

                                         CONSTRAINT ux_product_transformations_transaction UNIQUE (transaction_id)
);

-- =========================
-- 3) CREATE PRODUCT_TRANSFORMATION_INPUTS TABLE
-- =========================
CREATE TABLE product_transformation_inputs (
                                               product_transformation_input_id BIGSERIAL PRIMARY KEY,
                                               product_transformation_id BIGINT NOT NULL,
                                               product_id BIGINT NOT NULL,
                                               description TEXT,
                                               quantity NUMERIC(12,3) NOT NULL,

                                               CONSTRAINT fk_transformation_inputs_transformation
                                                   FOREIGN KEY (product_transformation_id)
                                                       REFERENCES product_transformations(product_transformation_id),

                                               CONSTRAINT fk_transformation_inputs_product
                                                   FOREIGN KEY (product_id)
                                                       REFERENCES products(product_id),

                                               CONSTRAINT ck_transformation_inputs_qty_pos CHECK (quantity > 0)
);

-- =========================
-- 4) CREATE PRODUCT_TRANSFORMATION_OUTPUTS TABLE
-- =========================
CREATE TABLE product_transformation_outputs (
                                                product_transformation_output_id BIGSERIAL PRIMARY KEY,
                                                product_transformation_id BIGINT NOT NULL,
                                                product_id BIGINT NOT NULL,
                                                description TEXT,
                                                quantity NUMERIC(12,3) NOT NULL,

                                                CONSTRAINT fk_transformation_outputs_transformation
                                                    FOREIGN KEY (product_transformation_id)
                                                        REFERENCES product_transformations(product_transformation_id),

                                                CONSTRAINT fk_transformation_outputs_product
                                                    FOREIGN KEY (product_id)
                                                        REFERENCES products(product_id),

                                                CONSTRAINT ck_transformation_outputs_qty_pos CHECK (quantity > 0)
);

-- =========================
-- 5) INDEXES
-- =========================
CREATE INDEX idx_product_transformations_warehouse
    ON product_transformations(warehouse_id);

CREATE INDEX idx_transformation_inputs_transformation
    ON product_transformation_inputs(product_transformation_id);

CREATE INDEX idx_transformation_outputs_transformation
    ON product_transformation_outputs(product_transformation_id);

CREATE INDEX idx_transformation_inputs_product
    ON product_transformation_inputs(product_id);

CREATE INDEX idx_transformation_outputs_product
    ON product_transformation_outputs(product_id);