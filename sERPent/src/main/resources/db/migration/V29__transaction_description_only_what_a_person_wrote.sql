-- V29__transaction_description_only_what_a_person_wrote.sql
--
-- `transactions.description` pasa a llevar SOLO lo que escribió una persona.
--
-- QUÉ ESTABA MAL
-- Cuando el operador no escribía un motivo, el servicio metía una oración armada por
-- concatenación:
--
--     "Inventory adjustment for product 5 in warehouse 2"
--     "Transfer of product 5 from warehouse 1 to warehouse 3"
--
-- En inglés, con los ids en vez de los nombres, y guardada en la base. La pantalla la
-- mostraba bajo el encabezado "Descripción", que promete las palabras del operador y estaba
-- entregando las de una máquina. Es el mismo defecto que V28 arregló en `inventory_movements`
-- —texto de operador congelado en una columna— en otro campo.
--
-- POR QUÉ ESTA MIGRACIÓN EXISTE, SI LA DE LOS ORÍGENES NO NECESITÓ UNA
-- Con los orígenes alcanzó con cambiar el código: la pantalla dejó de leer `note` y pasó a
-- componer la frase, así que los registros viejos se arreglaron solos.
--
-- Acá no pasa lo mismo, y la diferencia importa. `description` sigue leyéndose, porque
-- legítimamente guarda el motivo que escribió el operador. Mirando la fila no hay forma de
-- distinguir un motivo de una oración generada: las dos son texto en la misma columna. Si el
-- histórico no se toca, esas frases en inglés se siguen mostrando para siempre.
--
-- QUÉ SE BORRA Y POR QUÉ NO SE BORRA DE MÁS
-- Solo las filas cuyo texto coincide EXACTAMENTE con la forma que generaba el código, con
-- ids numéricos incluidos. No es un LIKE laxo: '%adjustment%' habría borrado el motivo de
-- alguien que escribió "ajuste por rotura". Un motivo humano que además calce carácter por
-- carácter con la plantilla en inglés y con los ids que le tocaron a esa misma fila no es un
-- riesgo real.
--
-- Nada se pierde de lo que esas frases decían: el tipo está en `transactions.type`, los
-- depósitos salen de los movimientos que la transacción dejó, y el producto está en los
-- ítems. Todo eso ya se compone al mostrar.

-- ---------------------------------------------------------------------------
-- 1) Los ajustes: "Inventory adjustment for product <id> in warehouse <id>"
-- ---------------------------------------------------------------------------
UPDATE transactions
SET description = NULL
WHERE type = 'ADJUSTMENT'
  AND description LIKE 'Inventory adjustment for product % in warehouse %';

-- ---------------------------------------------------------------------------
-- 2) Las transferencias: "Transfer of product <id> from warehouse <id> to warehouse <id>"
-- ---------------------------------------------------------------------------
UPDATE transactions
SET description = NULL
WHERE type = 'TRANSFER'
  AND description LIKE 'Transfer of product % from warehouse % to warehouse %';

-- ---------------------------------------------------------------------------
-- 3) Las etiquetas de tipo en los renglones
-- ---------------------------------------------------------------------------
-- `transaction_details.description` guardaba "Stock adjustment", "Warehouse transfer" y
-- "Devolución": tres etiquetas de TIPO, y el tipo ya está en la transacción.
--
-- Hoy no se ven —la pantalla usa `productName ?? description`, y estas filas siempre tienen
-- producto— pero eso las vuelve una trampa y no un dato inocuo: alcanza con una fila cuyo
-- producto no resuelva para que la etiqueta congelada salga a la pantalla. Se van ahora, que
-- es cuando se sabe que no se ven.
--
-- La columna NO se elimina: sigue siendo el nombre del renglón para transacciones sin
-- producto, que es para lo que existe.
UPDATE transaction_details
SET description = NULL
WHERE description IN ('Stock adjustment', 'Warehouse transfer', 'Devolución');
