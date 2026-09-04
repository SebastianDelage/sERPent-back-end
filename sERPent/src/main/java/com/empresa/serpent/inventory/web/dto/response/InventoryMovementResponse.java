package com.empresa.serpent.inventory.web.dto.response;

import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.transactions.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un movimiento de inventario, con los datos que la pantalla necesita para decir DE DÓNDE
 * VIENE sin que nadie le haya escrito la frase.
 *
 * <p>El origen se componía antes en el backend y se guardaba en {@code note}: "Sale #9",
 * "Transfer #9 from warehouse 1", "Conteo: 9999.999, anterior: 12.530". Quedaba congelado en
 * inglés, con el ID del depósito en vez del nombre y con punto decimal, y arreglar el código
 * no corregía nada de lo ya registrado.
 *
 * <p>Ahora viajan los datos y la frase la arma la pantalla. El formato es presentación, no
 * dato — el mismo principio que sostiene el sistema de separadores del front, donde
 * formatear y leer son una función y su inversa.
 */
public record InventoryMovementResponse(
        Long id,
        MovementType movementType,
        BigDecimal quantity,
        BigDecimal unitCost,
        LocalDateTime createdAt,

        /** Solo el motivo que escribió el operador. Null si no escribió ninguno. */
        String note,

        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        Long transactionId,

        /**
         * Qué operación lo produjo: venta, compra, transferencia, transformación, ajuste.
         *
         * <p>NO es una columna nueva: se llega por la FK a {@code transactions}. Es la mitad
         * de lo que hacía falta para armar el texto, y no hacía falta guardar nada para
         * tenerla.
         */
        TransactionType transactionType,

        /** El depósito del otro lado, en una transferencia. Null en el resto. */
        Long counterpartWarehouseId,
        String counterpartWarehouseName,

        /** Lo contado y lo que había, en un ajuste. Null en el resto, y null en los ajustes
         *  anteriores a la migración: ver la nota de la entidad. */
        BigDecimal countedQuantity,
        BigDecimal previousQuantity
) {
}
