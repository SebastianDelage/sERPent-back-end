-- V28__movement_origin_as_data.sql
--
-- El origen de un movimiento pasa a ser DATO en vez de una frase armada a mano.
--
-- QUÉ ESTABA MAL
-- La columna `note` guardaba texto generado por concatenación en el servicio:
--
--     "Sale #9"                              -> en inglés
--     "Transfer #9 from warehouse 1"         -> en inglés y con el ID del depósito
--     "Transformation #1 input"              -> en inglés
--     "Conteo: 9999.999, anterior: 12.530"   -> BigDecimal.toString(), punto decimal
--
-- El último es el peor de todos. `BigDecimal.toString()` no conoce el locale y siempre
-- escribe punto, así que "12.530" en una pantalla de auditoría de stock se lee como doce
-- mil quinientos treinta cuando son 12,530. Un factor mil, en el único lugar donde se va a
-- mirar para averiguar qué pasó con la mercadería.
--
-- Y como el texto quedaba CONGELADO en la base, arreglar el código no arreglaba nada de lo
-- ya registrado.
--
-- QUÉ CAMBIA
-- El servicio guarda los datos que faltaban y la pantalla arma la frase al mostrar. El
-- formato es presentación, no dato: es el mismo principio que ya rige el sistema de
-- separadores decimales del front, donde formatear y leer son una sola función.
--
-- Efecto secundario buscado: los movimientos históricos se corrigen solos, porque el texto
-- deja de estar guardado y pasa a calcularse.
--
-- QUÉ SE AGREGA Y QUÉ NO
-- Solo lo que NO se puede derivar de lo que ya hay:
--
--   · El tipo de transacción NO se agrega: se llega por la FK a `transactions`.
--   · El nombre del depósito NO se agrega: ya se resuelve por la FK a `warehouses`.
--   · El número de transformación NO se agrega: la pantalla pasa a mostrar el número de
--     TRANSACCIÓN, que es 1:1 con la transformación (ux_product_transformations_transaction)
--     y que además es el mismo número que ya muestran venta y compra.
--
-- `note` NO desaparece: pasa a guardar únicamente lo que ESCRIBIÓ EL OPERADOR —el motivo de
-- un ajuste o de una transferencia—, que sí es un dato y no puede componerse.

-- ---------------------------------------------------------------------------
-- 1) El depósito de la contraparte, para las transferencias.
-- ---------------------------------------------------------------------------
-- Una transferencia produce dos movimientos: el OUT en el origen y el IN en el destino.
-- Cada fila conoce su propio depósito (warehouse_id) pero no el del otro lado, y el otro
-- lado es justamente lo que el operador necesita leer. No es derivable de la fila sola:
-- había que ir a buscar el movimiento hermano. Con esta columna cada movimiento se explica
-- por sí mismo, que es lo que corresponde en una tabla de auditoría.
ALTER TABLE inventory_movements
    ADD COLUMN counterpart_warehouse_id BIGINT;

ALTER TABLE inventory_movements
    ADD CONSTRAINT fk_inventory_movements_counterpart_warehouse
        FOREIGN KEY (counterpart_warehouse_id)
            REFERENCES warehouses(warehouse_id);

-- ---------------------------------------------------------------------------
-- 2) Los dos números del ajuste.
-- ---------------------------------------------------------------------------
-- `counted_quantity` es lo que la persona contó físicamente; `previous_quantity` es lo que
-- el sistema creía tener. La diferencia entre los dos es `quantity`, que ya está.
--
-- SE GUARDAN LOS DOS aunque uno sea derivable del otro, y es una excepción deliberada a la
-- regla de no duplicar: la derivación (IN suma, OUT resta) es exacta solo mientras se
-- sostenga esa convención de signo, que vive en el código. Son dos hechos independientes
-- del mismo evento —lo que el sistema creía y lo que la persona vio— y esta es la tabla que
-- existe para poder reconstruir qué pasó. Una tabla de auditoría que recalcula sus propios
-- hechos es una peor tabla de auditoría.
ALTER TABLE inventory_movements
    ADD COLUMN counted_quantity NUMERIC(12,3);

ALTER TABLE inventory_movements
    ADD COLUMN previous_quantity NUMERIC(12,3);

-- ---------------------------------------------------------------------------
-- 3) Backfill de la contraparte. ESTE SÍ SE PUEDE.
-- ---------------------------------------------------------------------------
-- El depósito del otro lado no se pierde: está en el movimiento hermano de la misma
-- transacción. Una transferencia mueve un solo producto (CreateWarehouseTransferRequest
-- recibe un productId), así que por transacción hay exactamente dos movimientos de
-- transferencia y el hermano es único.
--
-- Se escribe como subconsulta correlacionada y no con UPDATE...FROM para que la misma
-- sentencia valga en Postgres y en H2, donde vive el gemelo de esta migración.
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

-- ---------------------------------------------------------------------------
-- 4) Los dos números de los ajustes viejos: NO se recuperan, a propósito.
-- ---------------------------------------------------------------------------
-- Quedan en NULL. Sí existen dentro del texto de `note`, pero recuperarlos exigiría parsear
-- "Conteo: 9999.999, anterior: 12.530" — y ese texto es ambiguo POR EL BUG QUE ESTA
-- MIGRACIÓN ARREGLA: no hay forma de saber si "12.530" son doce mil quinientos treinta o
-- 12,530, que es exactamente la razón por la que este trabajo existe.
--
-- Inventar un criterio de desambiguación para escribirlo como dato sería convertir una
-- lectura dudosa en un número que después nadie va a volver a cuestionar. Se prefiere no
-- tener el dato a tenerlo mal: la pantalla muestra el texto viejo tal cual para esas filas,
-- con su ambigüedad a la vista, y los ajustes nuevos salen bien desde el primer día.
