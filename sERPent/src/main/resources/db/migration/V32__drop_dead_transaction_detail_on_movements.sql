-- V32__drop_dead_transaction_detail_on_movements.sql
--
-- Se elimina `inventory_movements.transaction_detail_id`, que no la usa nadie.
--
-- QUÉ ERA
-- La agregó V2, con su clave foránea y su índice, para colgar cada movimiento del renglón
-- exacto de la transacción que lo originó. Esa idea nunca se terminó de implementar:
-- InventoryMovementEntity mapea product, warehouse, counterpartWarehouse y transaction, y
-- NO mapea esto. Relevado en todo el repositorio antes de borrarla: no aparece en ninguna
-- entidad, ningún repositorio, ninguna consulta nativa y ningún archivo del front.
--
-- NO CONFUNDIR con `transaction_details.transaction_detail_id`, que es la clave primaria de
-- esa tabla y sí se usa. Lo que se va es la columna del mismo nombre en OTRA tabla.
--
-- POR QUÉ AHORA, SI NO MOLESTABA
-- Porque era la única diferencia de esquema real entre los dos juegos de migraciones. Se
-- comparó el esquema final que produce cada uno y dio: las mismas 29 tablas, las mismas
-- columnas en todas salvo ésta, y los mismos índices salvo éste más el índice parcial de
-- product_suppliers.
--
-- Con esto, la ÚNICA diferencia que queda es ese índice parcial, que es legítima —H2 no
-- soporta índices únicos parciales— y por lo tanto explicable en una línea. Una diferencia
-- que se puede explicar se puede declarar, y una que se puede declarar la puede vigilar una
-- herramienta; doce diferencias sin explicar, no.
--
-- POR QUÉ NO HAY GEMELA EN db/migration-h2
-- Porque H2 nunca tuvo esta columna: su V1 es una línea de base aplastada, escrita después
-- de que V2 de Postgres ya existiera, y quien la escribió no la incluyó. Una migración allá
-- sería un DROP de algo que no está.
--
-- Es el único caso de esta serie donde el cambio va en un solo juego, y por eso conviene
-- decirlo: lo normal en este proyecto es que toda migración se escriba dos veces.

-- El orden importa: primero lo que depende de la columna, después la columna.
DROP INDEX IF EXISTS idx_inventory_movements_transaction_detail;

ALTER TABLE inventory_movements
    DROP CONSTRAINT IF EXISTS fk_inventory_movements_transaction_detail;

ALTER TABLE inventory_movements
    DROP COLUMN IF EXISTS transaction_detail_id;
