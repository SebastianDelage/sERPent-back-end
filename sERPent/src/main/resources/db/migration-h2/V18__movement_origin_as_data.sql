-- V18__movement_origin_as_data.sql
--
-- Gemelo H2 de db/migration/V28__movement_origin_as_data.sql. El porqué completo está allá;
-- acá va lo que cambia entre los dos juegos.
--
-- OJO CON LA NUMERACIÓN: los dos juegos ya divergieron y no hay nada que los mantenga
-- alineados. Los formatos de balanza son V27 en Postgres y V17 acá. Al agregar una
-- migración hay que buscar el siguiente número libre DE CADA JUEGO por separado; copiar el
-- número del otro lado deja un hueco o pisa una versión ya aplicada.
--
-- Las tres sentencias de esquema son idénticas a las de Postgres, y el UPDATE del backfill
-- se escribió como subconsulta correlacionada justamente para que sirviera igual acá: H2 no
-- tiene UPDATE ... FROM.

ALTER TABLE inventory_movements
    ADD COLUMN counterpart_warehouse_id BIGINT;

ALTER TABLE inventory_movements
    ADD CONSTRAINT fk_inventory_movements_counterpart_warehouse
        FOREIGN KEY (counterpart_warehouse_id)
            REFERENCES warehouses(warehouse_id);

ALTER TABLE inventory_movements
    ADD COLUMN counted_quantity NUMERIC(12,3);

ALTER TABLE inventory_movements
    ADD COLUMN previous_quantity NUMERIC(12,3);

-- Backfill de la contraparte de las transferencias. Sobre el fixture de desarrollo no
-- encuentra ninguna fila —el seed de V2 no carga transferencias— pero se deja igual para
-- que el juego H2 sea el mismo esquema Y el mismo procedimiento que producción. Un backfill
-- que solo corre de un lado es una diferencia que se descubre el día de la instalación.
UPDATE inventory_movements m
SET counterpart_warehouse_id = (
    SELECT o.warehouse_id
    FROM inventory_movements o
    WHERE o.transaction_id = m.transaction_id
      AND o.movement_id <> m.movement_id
      AND o.movement_type IN ('TRANSFER_IN', 'TRANSFER_OUT')
)
WHERE m.movement_type IN ('TRANSFER_IN', 'TRANSFER_OUT')
  AND m.transaction_id IS NOT NULL;
