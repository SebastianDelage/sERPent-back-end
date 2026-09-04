-- V20__clear_generated_movement_notes.sql
--
-- Gemelo H2 de db/migration/V30__clear_generated_movement_notes.sql. El porqué completo
-- está allá, incluida la razón por la que los 'Conteo: %, anterior: %' de los ajustes NO se
-- tocan.
--
-- OJO CON LA NUMERACIÓN: los dos juegos divergieron hace rato y nada los alinea. Los
-- orígenes de movimiento son V28 allá y V18 acá; las descripciones, V29 y V19; esta, V30 y
-- V20. Al agregar una migración hay que buscar el siguiente número libre DE CADA JUEGO por
-- separado.
--
-- Las cuatro sentencias son idénticas a las de Postgres: UPDATE con LIKE, sin nada propio de
-- ningún motor. Se repiten en vez de compartirse porque los dos juegos son independientes
-- por diseño.

UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Sale #%'
   OR note LIKE 'Purchase #%';

UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Transfer #%';

UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Transformation #%';

UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Devolución de la venta #%';
