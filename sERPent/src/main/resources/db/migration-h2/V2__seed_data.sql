-- V2__seed_data.sql
-- H2 seed data for sERPent ERP (core + inventory)

-- =========================
-- USERS
-- =========================
INSERT INTO users (user_id, name, last_name, username, password_hash, email, active, created_at)
VALUES
    (1, 'Admin', 'System', 'admin', 'dev-only-password', 'admin@serpent.com', TRUE, CURRENT_TIMESTAMP);

-- =========================
-- PAYMENT METHODS
-- =========================
INSERT INTO payment_methods (payment_method_id, name, active)
VALUES
    (1, 'Cash', TRUE),
    (2, 'Transfer', TRUE);

-- =========================
-- PRODUCTS
-- =========================
INSERT INTO products (
    product_id,
    name,
    description,
    price,
    sku,
    active,
    created_at,
    minimum_stock,
    reorder_point,
    reorder_quantity,
    unit_of_measure
)
VALUES
    (1, 'Pollo entero', 'Whole chicken', 2500, 'POLLO001', TRUE, CURRENT_TIMESTAMP, 20, 25, 50, 'UNIT'),
    (2, 'Pata muslo', 'Chicken leg quarter', 1800, 'POLLO002', TRUE, CURRENT_TIMESTAMP, 20, 30, 50, 'KG'),
    (3, 'Milanesa de pollo', 'Chicken milanese', 3000, 'POLLO003', TRUE, CURRENT_TIMESTAMP, NULL, NULL, NULL, 'KG');

-- =========================
-- SUPPLIERS
-- =========================
INSERT INTO suppliers (supplier_id, name, document_type, document_number, tax_condition, phone, email, active, created_at)
VALUES
    (1, 'Proveedor Central', 'CUIT', '30-12345678-9', 'Responsable Inscripto', '2235551111', 'proveedor@test.com', TRUE, CURRENT_TIMESTAMP);

-- =========================
-- PRODUCT SUPPLIERS
-- =========================
INSERT INTO product_suppliers (product_supplier_id, product_id, supplier_id, cost_price, preferred, active, lead_time_days)
VALUES
    (1, 1, 1, 3000, TRUE, TRUE, 2),
    (2, 2, 1, 3200, FALSE, TRUE, 2);

-- =========================
-- EXPENSE CATEGORIES
-- =========================
INSERT INTO expense_categories (expense_category_id, name, description, active)
VALUES
    (1, 'Insumos', 'Compra de insumos del negocio', TRUE);

-- =========================
-- WAREHOUSES
-- =========================
INSERT INTO warehouses (warehouse_id, name, active, created_at)
VALUES
    (1, 'Depósito Central', TRUE, CURRENT_TIMESTAMP),
    (2, 'Sucursal Norte', TRUE, CURRENT_TIMESTAMP),
    (3, 'Depósito Inactivo', FALSE, CURRENT_TIMESTAMP);

-- =========================
-- TRANSACTIONS
-- =========================
INSERT INTO transactions (transaction_id, date, type, status, total, payment_method_id, created_by_user_id, description)
VALUES
    (1, CURRENT_TIMESTAMP, 'SALE', 'CONFIRMED', 9100, 1, 1, 'Venta mostrador'),
    (2, CURRENT_TIMESTAMP, 'EXPENSE', 'CONFIRMED', 3000, 2, 1, 'Compra insumos'),
    (3, CURRENT_TIMESTAMP, 'PURCHASE', 'CONFIRMED', 46000, 1, 1, 'Compra inicial de mercadería');

-- =========================
-- TRANSACTION DETAILS
-- =========================
INSERT INTO transaction_details (transaction_detail_id, transaction_id, product_id, description, quantity, unit_price, subtotal)
VALUES
    -- Sale #1
    (1, 1, 1, 'Pollo entero', 1, 4500, 4500),
    (2, 1, 2, 'Pata muslo', 1, 4600, 4600),

    -- Purchase #1
    (3, 3, 1, 'Pollo entero', 10, 3000, 30000),
    (4, 3, 2, 'Pata muslo', 5, 3200, 16000);

-- =========================
-- SALES
-- =========================
INSERT INTO sales (sale_id, transaction_id, customer_name, invoice_number, tax_total)
VALUES
    (1, 1, 'Consumidor Final', 'A-0001-00000001', 0);

-- =========================
-- EXPENSES
-- =========================
INSERT INTO expenses (expense_id, transaction_id, supplier_id, expense_category_id, receipt_number, reimbursable)
VALUES
    (1, 2, 1, 1, 'REC-001', FALSE);

-- =========================
-- PURCHASES
-- =========================
INSERT INTO purchases (purchase_id, transaction_id, supplier_id, warehouse_id, receipt_number, notes)
VALUES
    (1, 3, 1, 1, 'PUR-001', 'Compra inicial de mercadería');

-- =========================
-- INITIAL INVENTORY MOVEMENTS
-- =========================
INSERT INTO inventory_movements (
    movement_id,
    product_id,
    warehouse_id,
    transaction_id,
    movement_type,
    quantity,
    unit_cost,
    created_at,
    note
)
VALUES
    -- Initial stock in warehouse 1
    (1, 1, 1, NULL, 'IN', 20, 3000, CURRENT_TIMESTAMP, 'Initial stock load'),
    (2, 2, 1, NULL, 'IN', 20, 3200, CURRENT_TIMESTAMP, 'Initial stock load'),
    (3, 3, 1, NULL, 'IN', 15, 3500, CURRENT_TIMESTAMP, 'Initial stock load'),

    -- Initial stock in warehouse 2
    (4, 1, 2, NULL, 'IN', 8, 3000, CURRENT_TIMESTAMP, 'Initial stock load'),
    (5, 3, 2, NULL, 'IN', 5, 3500, CURRENT_TIMESTAMP, 'Initial stock load'),

    -- Inventory impact of seeded sale #1 in warehouse 1
    (6, 1, 1, 1, 'OUT', 1, NULL, CURRENT_TIMESTAMP, 'Sale #1'),
    (7, 2, 1, 1, 'OUT', 1, NULL, CURRENT_TIMESTAMP, 'Sale #1'),

    -- Inventory impact of seeded purchase #1 in warehouse 1
    (8, 1, 1, 3, 'IN', 10, 3000, CURRENT_TIMESTAMP, 'Purchase #3'),
    (9, 2, 1, 3, 'IN', 5, 3200, CURRENT_TIMESTAMP, 'Purchase #3');

-- =========================
-- INITIAL INVENTORY SNAPSHOT
-- =========================
INSERT INTO inventory_stock_snapshot (
    snapshot_id,
    product_id,
    warehouse_id,
    current_stock,
    updated_at,
    last_movement_id
)
VALUES
    (1, 1, 1, 29, CURRENT_TIMESTAMP, 8),
    (2, 2, 1, 24, CURRENT_TIMESTAMP, 9),
    (3, 3, 1, 15, CURRENT_TIMESTAMP, 3),
    (4, 1, 2, 8, CURRENT_TIMESTAMP, 4),
    (5, 3, 2, 5, CURRENT_TIMESTAMP, 5);

-- =========================
-- RESET IDENTITY / AUTOINCREMENT
-- =========================
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 2;
ALTER TABLE payment_methods ALTER COLUMN payment_method_id RESTART WITH 3;
ALTER TABLE products ALTER COLUMN product_id RESTART WITH 4;
ALTER TABLE suppliers ALTER COLUMN supplier_id RESTART WITH 2;
ALTER TABLE product_suppliers ALTER COLUMN product_supplier_id RESTART WITH 3;
ALTER TABLE expense_categories ALTER COLUMN expense_category_id RESTART WITH 2;
ALTER TABLE warehouses ALTER COLUMN warehouse_id RESTART WITH 4;
ALTER TABLE transactions ALTER COLUMN transaction_id RESTART WITH 4;
ALTER TABLE transaction_details ALTER COLUMN transaction_detail_id RESTART WITH 5;
ALTER TABLE sales ALTER COLUMN sale_id RESTART WITH 2;
ALTER TABLE expenses ALTER COLUMN expense_id RESTART WITH 2;
ALTER TABLE purchases ALTER COLUMN purchase_id RESTART WITH 2;
ALTER TABLE product_transformations ALTER COLUMN product_transformation_id RESTART WITH 1;
ALTER TABLE product_transformation_inputs ALTER COLUMN product_transformation_input_id RESTART WITH 1;
ALTER TABLE product_transformation_outputs ALTER COLUMN product_transformation_output_id RESTART WITH 1;
ALTER TABLE inventory_movements ALTER COLUMN movement_id RESTART WITH 10;
ALTER TABLE inventory_stock_snapshot ALTER COLUMN snapshot_id RESTART WITH 6;