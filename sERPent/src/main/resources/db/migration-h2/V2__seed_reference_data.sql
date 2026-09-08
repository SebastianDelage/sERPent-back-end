-- V2__seed_reference_data.sql
--
-- DATOS DE REFERENCIA: los que la app necesita para funcionar, no para verse llena.
--
-- Sin ningún medio de pago no se puede registrar una venta, y sin ninguna categoría de gasto
-- no se puede cargar un gasto. Eso los separa de los datos de demostración, que se fueron a
-- db/seed-dev/V2_1__seed_demo_data.sql y que el perfil de test no carga: una instalación sin
-- productos de mentira sigue siendo usable, una sin medios de pago no.
--
-- ES LA MISMA DIVISIÓN QUE HACE EL MERCADO. Odoo separa en el manifiesto de cada módulo
-- `data` de `demo`, y se instala con o sin lo segundo. Dynamics BC separa lo que instala el
-- módulo de la empresa de demostración Cronus. Lo que este archivo tenía antes era las dos
-- cosas mezcladas.
--
-- OJO, ESTO NO ESTÁ EN EL JUEGO POSTGRES
-- db/migration no inserta absolutamente nada, ni siquiera esto, así que una instalación real
-- arranca sin medios de pago y el UPDATE de is_cash de V25 corre contra una tabla vacía.
-- Separar la referencia deja eso listo para llevarlo a Postgres, pero llevarlo es una
-- decisión del runbook de instalación y no se toma acá.

-- =========================
-- MEDIOS DE PAGO
-- =========================
-- V15 marca is_cash sobre el de id más bajo cuyo nombre parezca efectivo. Con estas dos filas
-- presentes, ese UPDATE tiene sobre qué correr.
INSERT INTO payment_methods (payment_method_id, name, active)
VALUES
    (1, 'Cash', TRUE),
    (2, 'Transfer', TRUE);

-- =========================
-- CATEGORÍAS DE GASTO
-- =========================
INSERT INTO expense_categories (expense_category_id, name, description, active)
VALUES
    (1, 'Insumos', 'Compra de insumos del negocio', TRUE);

-- =========================
-- REINICIO DE LOS CONTADORES DE IDENTIDAD
-- =========================
-- Las filas de arriba traen su id escrito a mano, así que sin esto el primer INSERT que haga
-- la app chocaría contra una clave ya usada.
ALTER TABLE payment_methods ALTER COLUMN payment_method_id RESTART WITH 3;
ALTER TABLE expense_categories ALTER COLUMN expense_category_id RESTART WITH 2;
