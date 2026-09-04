-- V21__reason_lives_in_the_transaction.sql
--
-- Gemelo H2 de db/migration/V31__reason_lives_in_the_transaction.sql. El porqué completo
-- está allá, incluida la razón por la que expenses.notes NO se toca.
--
-- OJO CON LA NUMERACIÓN: los dos juegos divergieron hace rato. Los orígenes de movimiento
-- son V28 allá y V18 acá; las descripciones, V29 y V19; las notas generadas, V30 y V20;
-- esta, V31 y V21. Al agregar una migración hay que buscar el siguiente número libre DE
-- CADA JUEGO por separado.
--
-- Las sentencias son idénticas: UPDATE con subconsulta correlacionada y EXISTS, que es lo
-- que funciona en los dos motores. H2 no tiene UPDATE ... FROM, igual que en V28.

-- ---------------------------------------------------------------------------
-- 1) Compras: la nota pasa a la descripción de su transacción
-- ---------------------------------------------------------------------------
-- Solo donde la descripción está vacía, que es siempre —el formulario nunca mandó ese
-- campo— pero se escribe la condición igual: si algún día algo la escribió, lo que puso una
-- persona no se pisa con esto.
UPDATE transactions t
SET description = (
    SELECT p.notes
    FROM purchases p
    WHERE p.transaction_id = t.transaction_id
)
WHERE t.description IS NULL
  AND EXISTS (
    SELECT 1
    FROM purchases p
    WHERE p.transaction_id = t.transaction_id
      AND p.notes IS NOT NULL
      AND p.notes <> ''
  );

-- ---------------------------------------------------------------------------
-- 2) Transformaciones: idéntico
-- ---------------------------------------------------------------------------
UPDATE transactions t
SET description = (
    SELECT x.notes
    FROM product_transformations x
    WHERE x.transaction_id = t.transaction_id
)
WHERE t.description IS NULL
  AND EXISTS (
    SELECT 1
    FROM product_transformations x
    WHERE x.transaction_id = t.transaction_id
      AND x.notes IS NOT NULL
      AND x.notes <> ''
  );

-- ---------------------------------------------------------------------------
-- 3) Devoluciones: no hay nada que mover
-- ---------------------------------------------------------------------------
-- `sale_returns.reason` guardaba una copia de lo que ya está en
-- `transactions.description`, escrita por el mismo servicio en la misma llamada. No se
-- copia nada porque el destino ya lo tiene; solo se limpia el duplicado para que no quede
-- una segunda versión del texto lista para desincronizarse.
--
-- Se limpia únicamente donde la transacción efectivamente tiene el motivo. Si por alguna
-- fila vieja la descripción estuviera vacía y el reason no, el reason es el único rastro y
-- se queda.
UPDATE sale_returns r
SET reason = NULL
WHERE EXISTS (
    SELECT 1
    FROM transactions t
    WHERE t.transaction_id = r.transaction_id
      AND t.description IS NOT NULL
      AND t.description <> ''
  );

-- ---------------------------------------------------------------------------
-- 4) Las notas movidas se limpian en su columna vieja
-- ---------------------------------------------------------------------------
-- Después de los pasos 1 y 2 el texto está en los dos lados. Se borra el de origen por la
-- misma razón que el reason de la devolución: dos copias del mismo texto es la condición
-- previa a que se separen.
UPDATE purchases p
SET notes = NULL
WHERE EXISTS (
    SELECT 1
    FROM transactions t
    WHERE t.transaction_id = p.transaction_id
      AND t.description = p.notes
  );

UPDATE product_transformations x
SET notes = NULL
WHERE EXISTS (
    SELECT 1
    FROM transactions t
    WHERE t.transaction_id = x.transaction_id
      AND t.description = x.notes
  );
