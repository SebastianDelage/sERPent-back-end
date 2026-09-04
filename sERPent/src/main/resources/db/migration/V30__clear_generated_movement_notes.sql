-- V30__clear_generated_movement_notes.sql
--
-- Se borra de `inventory_movements.note` el texto que escribió una máquina.
--
-- QUÉ QUEDÓ PENDIENTE DE V28
-- V28 sacó del CÓDIGO las frases generadas —el servicio dejó de escribirlas y la pantalla
-- pasó a componer el origen a partir del tipo y de los depósitos— pero NO tocó una sola fila:
-- tiene cero sentencias `SET note`. Se dio por hecho que el histórico se corregía solo, como
-- pasó con el origen, y no fue así: el origen se arregló porque la pantalla dejó de leer la
-- columna, mientras que `note` se sigue leyendo, porque legítimamente guarda el motivo que
-- escribió una persona.
--
-- Resultado: todo movimiento anterior a V28 sigue mostrando su frase vieja debajo del nombre
-- del producto.
--
--     "Sale #9"                            -> en inglés
--     "Purchase #9"                        -> en inglés
--     "Transfer #9 from warehouse 1"       -> en inglés y con el ID del depósito
--     "Transformation #1 input" / "output" -> en inglés
--     "Devolución de la venta #12"         -> en español, pero igual de compuesto
--
-- Las cinco son lo mismo: tipo de operación más número de documento, o sea exactamente lo
-- que la columna Origen ya arma al mostrar, sin nada guardado.
--
-- POR QUÉ AHORA, SI LA PANTALLA DEJA DE MOSTRAR `note`
-- Porque el defecto no es que se vean: es que están escritas. Mientras sigan en la columna,
-- cualquier consulta, export o pantalla futura las vuelve a sacar a la luz, y en inglés. La
-- regla del proyecto es que ningún texto que vea el operador se congele en la base, y esto
-- lo es aunque hoy nadie lo mire.
--
-- QUÉ NO SE BORRA Y POR QUÉ
-- Los 'Conteo: %, anterior: %' de los ajustes se QUEDAN. Ahí vive el único rastro de esos
-- dos números para los ajustes anteriores a V28: la migración no pudo recuperarlos a
-- countedQuantity/previousQuantity porque el texto es ambiguo —"12.530" puede ser doce mil
-- quinientos treinta o 12,530, que es el bug que V28 arregló— y se prefirió no tener el dato
-- a inventarlo. Borrar la frase ahora sería destruir esa evidencia por prolijidad. La
-- decisión está escrita en V28 y no cambia acá.
--
-- El LIKE va contra la plantilla EXACTA, con el numeral y el id, igual que en V29. Un
-- '%transfer%' habría borrado el motivo de alguien que escribió sobre una transferencia.

-- ---------------------------------------------------------------------------
-- 1) Venta y compra: "Sale #<id>" / "Purchase #<id>"
-- ---------------------------------------------------------------------------
UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Sale #%'
   OR note LIKE 'Purchase #%';

-- ---------------------------------------------------------------------------
-- 2) Transferencia: "Transfer #<id> from warehouse <id>"
-- ---------------------------------------------------------------------------
-- También la forma sin el sufijo, por si alguna versión anterior escribió solo el número.
UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Transfer #%';

-- ---------------------------------------------------------------------------
-- 3) Transformación: "Transformation #<id> input" y "... output"
-- ---------------------------------------------------------------------------
UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Transformation #%';

-- ---------------------------------------------------------------------------
-- 4) Devolución: "Devolución de la venta #<id>"
-- ---------------------------------------------------------------------------
-- Esta es la única de las cinco que el código seguía escribiendo hasta este cambio, y la
-- peor de todas: le ganaba al motivo real. El operador escribía "el cliente devolvió por mal
-- olor", eso iba a transactions.description, y la pantalla de auditoría mostraba la frase
-- armada en su lugar.
UPDATE inventory_movements
SET note = NULL
WHERE note LIKE 'Devolución de la venta #%';
