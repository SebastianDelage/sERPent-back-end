-- =========================
-- USERS
-- =========================
INSERT INTO users (user_id, name, last_name, username, password_hash, email, active, created_at)
VALUES
    (1, 'Admin', 'System', 'admin', '123', 'admin@serpent.com', TRUE, CURRENT_TIMESTAMP);

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
INSERT INTO products (product_id, name, description, price, sku, active, created_at)
VALUES
    (1, 'Pollo entero', 'Pollo fresco entero', 4500, 'POLLO-001', TRUE, CURRENT_TIMESTAMP),
    (2, 'Pata muslo', 'Combo pata y muslo', 4600, 'POLLO-002', TRUE, CURRENT_TIMESTAMP),
    (3, 'Milanesa de pollo', 'Milanesa preparada', 5200, 'POLLO-003', TRUE, CURRENT_TIMESTAMP);

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
-- TRANSACTIONS
-- =========================
INSERT INTO transactions (transaction_id, date, type, status, total, payment_method_id, created_by_user_id, description)
VALUES
    (1, CURRENT_TIMESTAMP, 'SALE', 'CONFIRMED', 9100, 1, 1, 'Venta mostrador'),
    (2, CURRENT_TIMESTAMP, 'EXPENSE', 'CONFIRMED', 3000, 2, 1, 'Compra insumos');

-- =========================
-- TRANSACTION DETAILS
-- =========================
INSERT INTO transaction_details (transaction_detail_id, transaction_id, product_id, description, quantity, unit_price, subtotal)
VALUES
    (1, 1, 1, 'Pollo entero', 1, 4500, 4500),
    (2, 1, 2, 'Pata muslo', 1, 4600, 4600);

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