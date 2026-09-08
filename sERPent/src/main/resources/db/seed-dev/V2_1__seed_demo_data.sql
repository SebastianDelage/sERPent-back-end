-- V2_1__seed_demo_data.sql
--
-- DATOS DE DEMOSTRACIÓN. No son parte del esquema ni la app los necesita para funcionar:
-- son un negocio de mentira —tres productos de pollo, un proveedor, tres depósitos, una
-- venta, una compra y un gasto— que existe para que quien levanta el proyecto vea pantallas
-- con contenido en vez de tablas vacías.
--
-- POR QUÉ VIVE ACÁ Y NO EN db/migration-h2
-- Porque el perfil de test NO lo incluye en sus locations. Mientras esto era la migración V2
-- del juego H2, la suite corría contra una base con este negocio adentro, y por eso la suite
-- no podía usar Flyway: doce tests fallaban por datos ajenos —claves duplicadas, conteos de
-- siete donde el test esperaba dos— y la única salida era generar el esquema desde las
-- entidades con ddl-auto=create-drop. Eso significaba que los tests nunca veían el esquema
-- real, y ya ocultó un bug: la FK fk_inventory_stock_snapshot_last_movement no existe cuando
-- Hibernate genera el esquema, porque la entidad mapea last_movement_id como Long plano.
--
-- Separado, el perfil de test corre las migraciones de verdad con ddl-auto=validate.
--
-- QUÉ SÍ QUEDÓ COMO MIGRACIÓN
-- Los datos de REFERENCIA —medios de pago y categorías de gasto— en V2__seed_reference_data.
-- La app no arranca usable sin ellos, así que no son demostración. Es la misma división que
-- hacen Odoo entre `data` y `demo` en el manifiesto de cada módulo, y Dynamics BC entre los
-- datos que instala el módulo y la empresa de demostración Cronus.
--
-- POR QUÉ LA VERSIÓN ES 2.1 Y NO UNA POSTERIOR
-- Para que corra exactamente donde corría antes: después de V2 y antes de V3. Las migraciones
-- que vienen después la tocan —V3 completa transaction_type, V17 le pone el código de balanza
-- a dos productos, V18 a V21 limpian descripciones y notas generadas— y todas esperan estas
-- filas en ese punto de la secuencia. Moverla al final habría obligado a reescribirla contra
-- el esquema final; con 2.1 no cambia una sola línea de datos.

-- =========================
-- USUARIO ADMINISTRADOR
-- =========================
-- Va con la demostración y no con la referencia, aunque sin ningún usuario no se pueda entrar.
-- El motivo es que su hash está escrito en el repositorio: una contraseña conocida es
-- aceptable en una base H2 en memoria y es un problema el día que los datos de referencia se
-- lleven a Postgres. Cómo se crea el primer usuario de una instalación real es una decisión
-- del runbook de instalación, no de este archivo.
INSERT INTO users (user_id, name, last_name, username, password_hash, email, active, created_at)
VALUES
    (1, 'Admin', 'System', 'admin', '$2a$10$Ed/CfmUU/BQ40u1vk0ODVOGpWwial7OKrS6FdnVwe2RWWpmevW0le', 'admin@serpent.com', TRUE, CURRENT_TIMESTAMP);

-- =========================
-- PRODUCTOS
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
-- PROVEEDORES
-- =========================
INSERT INTO suppliers (supplier_id, name, document_type, document_number, tax_condition, phone, email, active, created_at)
VALUES
    (1, 'Proveedor Central', 'CUIT', '30-12345678-9', 'Responsable Inscripto', '2235551111', 'proveedor@test.com', TRUE, CURRENT_TIMESTAMP);

-- =========================
-- PRODUCTOS POR PROVEEDOR
-- =========================
INSERT INTO product_suppliers (product_supplier_id, product_id, supplier_id, cost_price, preferred, active, lead_time_days)
VALUES
    (1, 1, 1, 3000, TRUE, TRUE, 2),
    (2, 2, 1, 3200, FALSE, TRUE, 2);

-- =========================
-- DEPÓSITOS
-- =========================
-- El tercero está inactivo a propósito: es el único modo de ver en desarrollo cómo se
-- comportan los selectores cuando un depósito dejó de operar.
INSERT INTO warehouses (warehouse_id, name, active, created_at)
VALUES
    (1, 'Depósito Central', TRUE, CURRENT_TIMESTAMP),
    (2, 'Sucursal Norte', TRUE, CURRENT_TIMESTAMP),
    (3, 'Depósito Inactivo', FALSE, CURRENT_TIMESTAMP);

-- =========================
-- TRANSACCIONES
-- =========================
INSERT INTO transactions (transaction_id, date, type, status, total, payment_method_id, created_by_user_id, description)
VALUES
    (1, CURRENT_TIMESTAMP, 'SALE', 'CONFIRMED', 9100, 1, 1, 'Venta mostrador'),
    (2, CURRENT_TIMESTAMP, 'EXPENSE', 'CONFIRMED', 3000, 2, 1, 'Compra insumos'),
    (3, CURRENT_TIMESTAMP, 'PURCHASE', 'CONFIRMED', 46000, 1, 1, 'Compra inicial de mercadería');

-- =========================
-- RENGLONES DE LAS TRANSACCIONES
-- =========================
INSERT INTO transaction_details (transaction_detail_id, transaction_id, product_id, description, quantity, unit_price, subtotal)
VALUES
    -- Venta 1
    (1, 1, 1, 'Pollo entero', 1, 4500, 4500),
    (2, 1, 2, 'Pata muslo', 1, 4600, 4600),

    -- Compra 1
    (3, 3, 1, 'Pollo entero', 10, 3000, 30000),
    (4, 3, 2, 'Pata muslo', 5, 3200, 16000);

-- =========================
-- VENTAS
-- =========================
-- warehouse_id va cargado para que la venta sea atribuible a una sucursal: sin eso el reporte
-- consolidado nunca da la suma de los reportes por depósito en desarrollo, y eso se lee como
-- un error de filtrado cuando en realidad es una fila que no se puede asignar.
INSERT INTO sales (sale_id, transaction_id, customer_name, invoice_number, tax_total, warehouse_id)
VALUES
    (1, 1, 'Consumidor Final', 'A-0001-00000001', 0, 1);

-- =========================
-- GASTOS
-- =========================
INSERT INTO expenses (expense_id, transaction_id, supplier_id, expense_category_id, receipt_number, reimbursable)
VALUES
    (1, 2, 1, 1, 'REC-001', FALSE);

-- =========================
-- COMPRAS
-- =========================
INSERT INTO purchases (purchase_id, transaction_id, supplier_id, warehouse_id, receipt_number, notes)
VALUES
    (1, 3, 1, 1, 'PUR-001', 'Compra inicial de mercadería');

-- =========================
-- MOVIMIENTOS DE INVENTARIO INICIALES
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
    -- Stock inicial del depósito 1
    (1, 1, 1, NULL, 'IN', 20, 3000, CURRENT_TIMESTAMP, 'Initial stock load'),
    (2, 2, 1, NULL, 'IN', 20, 3200, CURRENT_TIMESTAMP, 'Initial stock load'),
    (3, 3, 1, NULL, 'IN', 15, 3500, CURRENT_TIMESTAMP, 'Initial stock load'),

    -- Stock inicial del depósito 2
    (4, 1, 2, NULL, 'IN', 8, 3000, CURRENT_TIMESTAMP, 'Initial stock load'),
    (5, 3, 2, NULL, 'IN', 5, 3500, CURRENT_TIMESTAMP, 'Initial stock load'),

    -- Impacto en inventario de la venta 1, en el depósito 1
    (6, 1, 1, 1, 'OUT', 1, NULL, CURRENT_TIMESTAMP, 'Sale #1'),
    (7, 2, 1, 1, 'OUT', 1, NULL, CURRENT_TIMESTAMP, 'Sale #1'),

    -- Impacto en inventario de la compra 1, en el depósito 1
    (8, 1, 1, 3, 'IN', 10, 3000, CURRENT_TIMESTAMP, 'Purchase #3'),
    (9, 2, 1, 3, 'IN', 5, 3200, CURRENT_TIMESTAMP, 'Purchase #3');

-- =========================
-- FOTO DE STOCK INICIAL
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
-- REINICIO DE LOS CONTADORES DE IDENTIDAD
-- =========================
-- Las filas de arriba traen su id escrito a mano, así que el contador de cada tabla quedó en
-- cero y el primer INSERT que haga la app chocaría contra una clave ya usada. Acá solo van
-- las tablas que este archivo llena; las de referencia reinician su contador en su propio
-- archivo. Las tres de transformaciones no tienen datos de demostración y reinician en 1,
-- que es donde ya estaban: se dejan escritas para que la lista sea la de todas las tablas
-- con identidad y no haya que adivinar si falta alguna.
ALTER TABLE users ALTER COLUMN user_id RESTART WITH 2;
ALTER TABLE products ALTER COLUMN product_id RESTART WITH 4;
ALTER TABLE suppliers ALTER COLUMN supplier_id RESTART WITH 2;
ALTER TABLE product_suppliers ALTER COLUMN product_supplier_id RESTART WITH 3;
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
