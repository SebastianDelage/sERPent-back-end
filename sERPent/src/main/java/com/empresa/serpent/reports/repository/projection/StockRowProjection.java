package com.empresa.serpent.reports.repository.projection;

import java.math.BigDecimal;

/**
 * Una fila de stock —un producto en un depósito— con los seis campos que la pantalla usa y
 * ningún otro.
 *
 * <h2>POR QUÉ EXISTE</h2>
 *
 * <p>Esta lectura es la más caliente de la app: la pantalla de venta la pide al abrirse y de
 * nuevo después de confirmar cada venta. Bajaba las filas como entidades gestionadas y después
 * leía el nombre del producto y el del depósito, que son relaciones perezosas. MEDIDO con las
 * estadísticas de Hibernate: diez filas costaban doce sentencias —una por la lista, diez por
 * los productos, una por el depósito—, para armar un DTO de seis campos escalares.
 *
 * <p>Con esta proyección son <b>dos</b>: la de la lista y la del depósito, que se resuelve una
 * sola vez. El costo dejó de crecer con el catálogo.
 *
 * <h2>POR QUÉ PROYECCIÓN Y NO UN @EntityGraph</h2>
 *
 * <p>Un grafo también bajaría el número, y es más barato de escribir. No se eligió por dos
 * razones. La primera es que StockQueryService entra por cuatro finders distintos según haya o
 * no producto y según el alcance por depósito, y uno de ellos es {@code findAll()}, heredado:
 * ponerle un grafo obligaría a redefinirlo y se lo comería también la reconstrucción de
 * snapshots, que no lo necesita. La segunda es que un grafo sigue trayendo las entidades
 * enteras —todas las columnas de producto y de depósito— al contexto de persistencia, para
 * leer dos nombres.
 *
 * <p>Y es lo que hace el mercado: ni Odoo, ni SAP B1, ni Dynamics BC usan proxies perezosos en
 * una lectura caliente; seleccionan los campos que necesitan. El proyecto ya lo hace en la otra
 * consulta de stock, {@code searchGroupedByProduct}, así que además hay precedente adentro.
 *
 * <h2>ES UNA INTERFAZ Y NO UN RECORD</h2>
 *
 * <p>Para no meter un DTO de la capa web en una consulta del repositorio. El armado de
 * {@code StockResponse} se queda en el servicio, igual que con {@link ProductStockProjection}.
 */
public interface StockRowProjection {

    Long getProductId();

    String getProductName();

    Long getWarehouseId();

    String getWarehouseName();

    BigDecimal getCurrentStock();

    /** Para que la pantalla pueda marcar mercadería parada en un depósito dado de baja. */
    Boolean getWarehouseActive();
}
