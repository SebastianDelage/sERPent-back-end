-- V19__transaction_description_only_what_a_person_wrote.sql
--
-- Gemelo H2 de db/migration/V29__transaction_description_only_what_a_person_wrote.sql. El
-- porqué completo está allá.
--
-- OJO CON LA NUMERACIÓN: los dos juegos divergieron hace rato. Los orígenes de movimiento
-- son V28 en Postgres y V18 acá; esta es V29 allá y V19 acá. Al agregar una migración hay
-- que buscar el siguiente número libre DE CADA JUEGO por separado.
--
-- Las tres sentencias son idénticas a las de Postgres: son UPDATE con LIKE e IN, sin nada
-- de sintaxis propia de un motor. Se repiten en vez de compartirse porque los dos juegos son
-- independientes por diseño, no porque acá haga falta algo distinto.

UPDATE transactions
SET description = NULL
WHERE type = 'ADJUSTMENT'
  AND description LIKE 'Inventory adjustment for product % in warehouse %';

UPDATE transactions
SET description = NULL
WHERE type = 'TRANSFER'
  AND description LIKE 'Transfer of product % from warehouse % to warehouse %';

UPDATE transaction_details
SET description = NULL
WHERE description IN ('Stock adjustment', 'Warehouse transfer', 'Devolución');
