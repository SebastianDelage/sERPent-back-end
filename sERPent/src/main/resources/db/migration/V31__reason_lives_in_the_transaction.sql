-- V31__reason_lives_in_the_transaction.sql
--
-- El motivo que escribe el operador pasa a vivir SIEMPRE en `transactions.description`.
--
-- QUÉ ESTABA MAL
-- El mismo dato —"por qué hice esto"— terminaba en cuatro columnas distintas según la
-- operación:
--
--     Compra          -> purchases.notes
--     Transformación  -> product_transformations.notes
--     Ajuste          -> transactions.description
--     Transferencia   -> transactions.description
--     Devolución      -> transactions.description Y sale_returns.reason
--     Venta           -> a ningún lado: el formulario no tiene campo
--
-- Las dos primeras son las graves: `purchases.notes` y `product_transformations.notes` NO
-- LAS LEE NINGUNA PANTALLA. Se escribían y ahí morían. Alguien que anotaba "faltaron dos
-- cajones, reclamar al proveedor" en una compra no lo volvía a ver nunca, en ningún lado.
--
-- La de la devolución es una duplicación pura: el mismo request.reason() en dos columnas,
-- que se separan en cuanto alguien edite una.
--
-- POR QUÉ LA TRANSACCIÓN Y NO EL RENGLÓN
-- El motivo es de la OPERACIÓN. Una transferencia genera dos movimientos y un motivo; una
-- compra de ocho renglones genera ocho movimientos y una nota. La transacción ya es el
-- ancla —todo movimiento tiene FK a ella— y `transactions.description` ya existía y estaba
-- vacía en compra y transformación, porque el formulario nunca mandó ese campo.
--
-- LAS COLUMNAS NO SE ELIMINAN, y esto es deliberado. Se aplica el mismo criterio que dejó a
-- `inventory_movements.note` como archivo: primero se deja de escribir, después se decide
-- si se borra. Una columna sin escritor no molesta a nadie; una columna borrada con datos
-- adentro no vuelve.
--
-- QUÉ NO SE TOCA: `expenses.notes`. Es la excepción y hay que decirla, porque comparte
-- nombre con las dos que sí se mueven. El gasto tiene DOS campos y muestra los dos, con
-- etiquetas distintas ("Descripción" y "Notas") en expense-detail: ahí `description` dice
-- qué es el gasto y `notes` es una observación aparte. Es el único lugar del sistema donde
-- los dos conceptos están separados a propósito y funcionando. Barrerlo junto con los otros
-- por llamarse igual habría destruido un dato que sí se ve.

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
