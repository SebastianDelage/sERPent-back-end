package com.empresa.serpent.inventory.domain;

import java.math.BigDecimal;

/**
 * La cascada de reposición por depósito, en un solo lugar del lado Java.
 *
 * <p>Para un (producto, depósito), cada una de las tres cifras —mínimo, punto de reposición
 * y cantidad— vale lo que diga el override del depósito si lo definió, y lo del producto si
 * no. Las tres se resuelven POR SEPARADO: un depósito puede adelantar su punto de reposición
 * sin tocar el piso, que es una afirmación distinta.
 *
 * <h2>Por qué esto no cubre las consultas SQL, y por qué eso es una decisión</h2>
 *
 * <p>La misma cascada está además escrita como {@code COALESCE} en JPQL (en
 * {@code InventoryStockSnapshotRepository}, tanto en la búsqueda paginada por producto como
 * en el reporte de reposición) y con la Criteria API en
 * {@code InventoryStockSnapshotSpecifications}. Esas corren DENTRO de la base y no pueden
 * llamar a este método: no hay forma de compartir una implementación entre la JVM y el motor
 * SQL.
 *
 * <p>Se evaluó unificarlas en una vista de base que expusiera los valores efectivos por
 * (producto, depósito), y se descartó a propósito: paga una migración espejada en H2 y
 * Postgres y toca tres consultas que hoy funcionan y coinciden numéricamente con este helper,
 * para un problema que todavía no se manifestó. En lugar de eso hay tests que comparan la vía
 * Java contra las vías SQL sobre los mismos datos, de modo que una divergencia futura falle
 * en CI en vez de aparecer en pantalla. Si alguna vez fallan, ahí la vista se justifica con
 * evidencia y no por prolijidad.
 *
 * <p>O sea: la duplicación del lado SQL es deliberada y está cubierta. Lo que NO era
 * deliberado, y por eso vive acá, era tener dos implementaciones distintas del mismo ternario
 * en dos servicios Java.
 */
public final class ReorderCascade {

    private ReorderCascade() {
    }

    /**
     * El valor que aplica: el propio del depósito si lo definió, el del producto si no.
     *
     * <p>Un {@code null} en {@code own} significa "heredo", nunca "sin valor": ese es
     * justamente el motivo de que las tres columnas del override sean nullable. Y si el
     * producto tampoco lo define, el resultado es null y quiere decir que esa cifra no
     * aplica en ningún nivel — un producto sin mínimo nunca está bajo mínimo, y uno sin
     * punto de reposición nunca entra al reporte.
     */
    public static BigDecimal resolve(BigDecimal own, BigDecimal fromProduct) {
        return own != null ? own : fromProduct;
    }
}
