-- V1__init_schema.sql
-- H2-compatible schema for sERPent ERP (core + inventory)

-- =========================
-- 1) USERS
-- =========================
CREATE TABLE users (
                       user_id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100),
                       username VARCHAR(80) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       email VARCHAR(150),
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                       CONSTRAINT ux_users_username UNIQUE (username),
                       CONSTRAINT ux_users_email UNIQUE (email)
);

-- =========================
-- 2) PAYMENT METHODS
-- =========================
CREATE TABLE payment_methods (
                                 payment_method_id BIGSERIAL PRIMARY KEY,
                                 name VARCHAR(80) NOT NULL,
                                 active BOOLEAN NOT NULL DEFAULT TRUE,

                                 CONSTRAINT ux_payment_methods_name UNIQUE (name)
);

-- =========================
-- 3) PRODUCTS
-- =========================
CREATE TABLE products (
                          product_id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(150) NOT NULL,
                          description TEXT,
                          price NUMERIC(19,4) NOT NULL DEFAULT 0,
                          sku VARCHAR(80),
                          active BOOLEAN NOT NULL DEFAULT TRUE,
                          minimum_stock NUMERIC(12,3),
                          reorder_point NUMERIC(12,3),
                          reorder_quantity NUMERIC(12,3),
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                          CONSTRAINT ux_products_sku UNIQUE (sku),
                          CONSTRAINT ck_products_price_nonneg CHECK (price >= 0)
);

-- =========================
-- 4) SUPPLIERS
-- =========================
CREATE TABLE suppliers (
                           supplier_id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(150) NOT NULL,
                           document_type VARCHAR(30),
                           document_number VARCHAR(40),
                           tax_condition VARCHAR(50),
                           phone VARCHAR(50),
                           email VARCHAR(150),
                           address TEXT,
                           notes TEXT,
                           active BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                           CONSTRAINT ux_suppliers_document UNIQUE (document_type, document_number)
);

-- =========================
-- 5) PRODUCT_SUPPLIERS
-- =========================
CREATE TABLE product_suppliers (
                                   product_supplier_id BIGSERIAL PRIMARY KEY,
                                   product_id BIGINT NOT NULL,
                                   supplier_id BIGINT NOT NULL,
                                   cost_price NUMERIC(19,4) NOT NULL DEFAULT 0,
                                   preferred BOOLEAN NOT NULL DEFAULT FALSE,
                                   active BOOLEAN NOT NULL DEFAULT TRUE,
                                   lead_time_days INTEGER,

                                   CONSTRAINT fk_product_suppliers_product
                                       FOREIGN KEY (product_id) REFERENCES products(product_id),

                                   CONSTRAINT fk_product_suppliers_supplier
                                       FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),

                                   CONSTRAINT ux_product_suppliers_product_supplier UNIQUE (product_id, supplier_id),
                                   CONSTRAINT ck_product_suppliers_cost_nonneg CHECK (cost_price >= 0),
                                   CONSTRAINT ck_product_suppliers_lead_time_nonneg CHECK (lead_time_days IS NULL OR lead_time_days >= 0)
);

-- H2 does not support this PostgreSQL partial unique index syntax
-- CREATE UNIQUE INDEX ux_product_suppliers_preferred_per_product
--     ON product_suppliers(product_id)
--     WHERE preferred = TRUE AND active = TRUE;

-- =========================
-- 6) EXPENSE CATEGORIES
-- =========================
CREATE TABLE expense_categories (
                                    expense_category_id BIGSERIAL PRIMARY KEY,
                                    name VARCHAR(120) NOT NULL,
                                    description TEXT,
                                    active BOOLEAN NOT NULL DEFAULT TRUE,

                                    CONSTRAINT ux_expense_categories_name UNIQUE (name)
);

-- =========================
-- 7) TRANSACTIONS
-- =========================
CREATE TABLE transactions (
                              transaction_id BIGSERIAL PRIMARY KEY,
                              date TIMESTAMP NOT NULL DEFAULT NOW(),
                              type VARCHAR(30) NOT NULL,
                              status VARCHAR(30) NOT NULL,
                              total NUMERIC(19,4) NOT NULL DEFAULT 0,
                              payment_method_id BIGINT,
                              created_by_user_id BIGINT NOT NULL,
                              description TEXT,

                              CONSTRAINT fk_transactions_payment_method
                                  FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_method_id),

                              CONSTRAINT fk_transactions_created_by_user
                                  FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),

                              CONSTRAINT ck_transactions_total_nonneg CHECK (total >= 0),

                              CONSTRAINT ck_transactions_type CHECK (
                                  type IN ('SALE','EXPENSE','PURCHASE','ADJUSTMENT','TRANSFER','RETURN')
                                  ),
                              CONSTRAINT ck_transactions_status CHECK (
                                  status IN ('PENDING','CONFIRMED','CANCELLED')
                                  )
);

-- =========================
-- 8) TRANSACTION_DETAILS
-- =========================
CREATE TABLE transaction_details (
                                     transaction_detail_id BIGSERIAL PRIMARY KEY,
                                     transaction_id BIGINT NOT NULL,
                                     product_id BIGINT,
                                     description TEXT,
                                     quantity NUMERIC(12,3) NOT NULL,
                                     unit_price NUMERIC(19,4) NOT NULL DEFAULT 0,
                                     subtotal NUMERIC(19,4) NOT NULL DEFAULT 0,

                                     CONSTRAINT fk_transaction_details_transaction
                                         FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                                     CONSTRAINT fk_transaction_details_product
                                         FOREIGN KEY (product_id) REFERENCES products(product_id),

                                     CONSTRAINT ck_transaction_details_qty_pos CHECK (quantity > 0),
                                     CONSTRAINT ck_transaction_details_unit_price_nonneg CHECK (unit_price >= 0),
                                     CONSTRAINT ck_transaction_details_subtotal_nonneg CHECK (subtotal >= 0)
);

-- =========================
-- 9) SALES
-- =========================
CREATE TABLE sales (
                       sale_id BIGSERIAL PRIMARY KEY,
                       transaction_id BIGINT NOT NULL,
                       customer_id BIGINT,
                       customer_name VARCHAR(150),
                       customer_document VARCHAR(60),
                       invoice_number VARCHAR(60),
                       tax_total NUMERIC(19,4),
                       cae VARCHAR(60),
                       due_date DATE,

                       CONSTRAINT fk_sales_transaction
                           FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                       CONSTRAINT ux_sales_transaction UNIQUE (transaction_id),
                       CONSTRAINT ux_sales_invoice_number UNIQUE (invoice_number),

                       CONSTRAINT ck_sales_tax_total_nonneg CHECK (tax_total IS NULL OR tax_total >= 0)
);

-- =========================
-- 10) SALE RETURNS
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

-- =========================
-- 11) EXPENSES
-- =========================
CREATE TABLE expenses (
                          expense_id BIGSERIAL PRIMARY KEY,
                          transaction_id BIGINT NOT NULL,
                          supplier_id BIGINT,
                          expense_category_id BIGINT NOT NULL,
                          receipt_number VARCHAR(80),
                          reimbursable BOOLEAN NOT NULL DEFAULT FALSE,
                          notes TEXT,

                          CONSTRAINT fk_expenses_transaction
                              FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                          CONSTRAINT fk_expenses_supplier
                              FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),

                          CONSTRAINT fk_expenses_category
                              FOREIGN KEY (expense_category_id) REFERENCES expense_categories(expense_category_id),

                          CONSTRAINT ux_expenses_transaction UNIQUE (transaction_id)
);

-- =========================
-- 12) WAREHOUSES
-- =========================
CREATE TABLE warehouses (
                            warehouse_id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(120) NOT NULL,
                            active BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                            CONSTRAINT ux_warehouses_name UNIQUE (name)
);

-- =========================
-- 13) PURCHASES
-- =========================
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

-- =========================
-- 14) INVENTORY_MOVEMENTS
-- =========================
CREATE TABLE inventory_movements (
                                     movement_id BIGSERIAL PRIMARY KEY,
                                     product_id BIGINT NOT NULL,
                                     warehouse_id BIGINT NOT NULL,
                                     transaction_id BIGINT,
                                     movement_type VARCHAR(30) NOT NULL,
                                     quantity NUMERIC(12,3) NOT NULL,
                                     unit_cost NUMERIC(19,4),
                                     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                     note TEXT,

                                     CONSTRAINT fk_inventory_movements_product
                                         FOREIGN KEY (product_id) REFERENCES products(product_id),

                                     CONSTRAINT fk_inventory_movements_warehouse
                                         FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),

                                     CONSTRAINT fk_inventory_movements_transaction
                                         FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),

                                     CONSTRAINT ck_inventory_movements_qty_pos CHECK (quantity > 0),
                                     CONSTRAINT ck_inventory_movements_unit_cost_nonneg CHECK (unit_cost IS NULL OR unit_cost >= 0),

                                     CONSTRAINT ck_inventory_movements_type CHECK (
                                         movement_type IN (
                                                           'IN',
                                                           'OUT',
                                                           'ADJUSTMENT_IN',
                                                           'ADJUSTMENT_OUT',
                                                           'TRANSFER_IN',
                                                           'TRANSFER_OUT',
                                                           'RETURN_IN'
                                             )
                                         )
);

-- =========================
-- 15) INVENTORY STOCK SNAPSHOT
-- =========================
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

-- =========================
-- INDEXES
-- =========================
CREATE INDEX idx_transactions_date ON transactions(date);
CREATE INDEX idx_transactions_type_date ON transactions(type, date);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_by_user ON transactions(created_by_user_id);
CREATE INDEX idx_transactions_payment_method ON transactions(payment_method_id);

CREATE INDEX idx_transaction_details_transaction ON transaction_details(transaction_id);
CREATE INDEX idx_transaction_details_product ON transaction_details(product_id);

CREATE INDEX idx_sale_returns_original_sale ON sale_returns(original_sale_id);

CREATE INDEX idx_expenses_supplier ON expenses(supplier_id);
CREATE INDEX idx_expenses_category ON expenses(expense_category_id);

CREATE INDEX idx_purchases_supplier ON purchases(supplier_id);
CREATE INDEX idx_purchases_warehouse ON purchases(warehouse_id);

CREATE INDEX idx_product_suppliers_supplier ON product_suppliers(supplier_id);
CREATE INDEX idx_product_suppliers_product ON product_suppliers(product_id);

CREATE INDEX idx_inventory_movements_product_warehouse
    ON inventory_movements(product_id, warehouse_id);

CREATE INDEX idx_inventory_movements_transaction
    ON inventory_movements(transaction_id);

CREATE INDEX idx_inventory_movements_created_at
    ON inventory_movements(created_at);

CREATE INDEX idx_inventory_stock_snapshot_product
    ON inventory_stock_snapshot(product_id);

CREATE INDEX idx_inventory_stock_snapshot_warehouse
    ON inventory_stock_snapshot(warehouse_id);

CREATE INDEX idx_inventory_stock_snapshot_current_stock
    ON inventory_stock_snapshot(current_stock);