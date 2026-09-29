package com.empresa.serpent.catalog.domain;

import java.math.BigDecimal;

/**
 * How far a product's price may move before it is worth asking about.
 *
 * <p>ES UN AVISO Y NO UN BLOQUEO, y esa es la decisión de diseño que manda sobre todo lo demás.
 * Un precio raro puede ser perfectamente legítimo —el pollo subió, una promoción, un producto que
 * se empezó a vender por kilo— y del otro lado del mostrador hay gente esperando. Un bloqueo que
 * se equivoca una vez por semana enseña a buscar cómo saltearlo; un aviso que se equivoca se
 * ignora y no hace daño.
 *
 * <p>ASÍ LO RESUELVE EL MERCADO [RECUERDO: de entrenamiento, no verificado contra la
 * documentación]:
 * <ul>
 *   <li><b>SAP Business One</b> tiene un "Deviation %" en el maestro de artículos: el sistema
 *       avisa cuando el precio se aparta más de ese porcentaje, y hay una autorización aparte
 *       para los casos que sí se quieren frenar. Es exactamente este modelo: umbral por artículo.
 *   <li><b>Odoo</b> distingue explícitamente "Warning" de "Blocking Message" en los avisos por
 *       producto: el primero se muestra y deja seguir, el segundo corta. Tener los dos niveles
 *       como conceptos separados es la parte que se copia.
 *   <li><b>Dynamics 365 Business Central</b> usa notificaciones no bloqueantes para esta familia
 *       de cosas (límite de crédito superado, por ejemplo): avisa arriba del documento y deja
 *       trabajar.
 * </ul>
 *
 * <p>Los tres coinciden en lo mismo: el umbral es POR ARTÍCULO, y avisar y bloquear son dos cosas
 * distintas que no conviene mezclar.
 */
public final class PriceAlertPolicy {

    /**
     * La tolerancia que se aplica cuando el producto no tiene una propia.
     *
     * <p>TREINTA POR CIENTO, y el número está elegido contra los dos errores que importan:
     * <ul>
     *   <li>Un cero de más multiplica por diez: 1.000 % de diferencia. Cualquier umbral razonable
     *       lo atrapa, así que el 30 % no está puesto para eso.
     *   <li>Está puesto para el otro: un dígito cambiado (1.700 → 4.700) da 176 %, y una coma
     *       corrida (170,00 → 1.700,00) da 900 %. Los dos saltan.
     *   <li>Y para NO saltar en lo normal: un aumento de lista del 10 % o 15 %, un redondeo, un
     *       ajuste por inflación mensual. Si avisara ahí, en dos semanas nadie lo lee.
     * </ul>
     *
     * <p>VIVE EN EL CÓDIGO Y NO EN LA BASE a propósito: es un número que se va a mover con la
     * experiencia del local, y moverlo tiene que ser un despliegue, no una migración sobre una
     * base con datos.
     */
    public static final BigDecimal DEFAULT_PERCENT = new BigDecimal("30.00");

    private PriceAlertPolicy() {
    }

    /**
     * The tolerance that actually applies to a product.
     *
     * @param configured lo que tiene el producto, o null si nunca se configuró
     */
    public static BigDecimal effectivePercent(BigDecimal configured) {
        if (configured == null || configured.signum() <= 0) {
            return DEFAULT_PERCENT;
        }
        return configured;
    }
}
