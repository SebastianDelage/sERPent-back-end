package com.empresa.serpent.shared.validation;

/**
 * Hasta dónde puede llegar un ajuste porcentual sobre un precio, y por qué el criterio no es
 * ni el de las cantidades ni el de los importes.
 *
 * <h2>POR QUÉ EXISTE</h2>
 *
 * <p>Es el tercer agujero de la misma familia, y el último de los que tenían riesgo. Hasta que
 * se escribió esta clase, un RECARGO no tenía cota en ninguna capa: ni {@code Validators.max}
 * en el formulario, ni {@code @Digits} ni {@code @DecimalMax} en el DTO, ni constante en el
 * proyecto. Lo único que lo limitaba era el {@code NUMERIC(9,4)} de la columna, o sea un
 * recargo del 99.999,9999%.
 *
 * <p>El DESCUENTO sí estaba acotado, en {@code -100}, por el servicio y por un CHECK de la
 * base. Esa cota es el límite natural: un descuento del 100% deja el precio en cero y más allá
 * el precio sería negativo. Un recargo no tiene ningún punto equivalente — el 100% duplica el
 * precio y la aritmética sigue funcionando igual de bien al 500% que al 5%.
 *
 * <h2>EL CRITERIO DE HOLGURA NO ES EL DE {@link MoneyLimits}</h2>
 *
 * <p>{@code MoneyLimits} yerra hacia arriba porque los precios suben solos: un techo bajo
 * bloquea una venta legítima el día que la inflación lo alcanza. <b>Un porcentaje no tiene esa
 * deriva.</b> Un recargo por tarjeta es una razón entre dos precios, no un precio: si mañana
 * todo vale diez veces más, el recargo por pagar con tarjeta sigue siendo del 10 o del 20%.
 * El argumento que obliga a ser generoso con los pesos no aplica acá, y por lo tanto el techo
 * puede ser mucho más ajustado sin arriesgar nada.
 *
 * <h2>DE DÓNDE SALE EL 100</h2>
 *
 * <p>Un recargo realista por tarjeta o transferencia en la granjita está entre el 10 y el 30%.
 * Cien es más del triple del extremo de esa banda, así que no bloquea ninguna configuración
 * plausible, y descarta el 200% que sería un error de tipeo y no una operación.
 *
 * <p>Además es el mismo número que el piso, con signo cambiado, y eso importa más de lo que
 * parece: el valor se guarda FIRMADO en una sola columna —negativo descuento, positivo
 * recargo—, así que un rango simétrico es un solo número, un solo rango y un solo modelo
 * mental. Un rango asimétrico como {@code [-100, +60]} obligaría a llevar dos números en cada
 * capa y a explicarle al operador por qué las dos direcciones no se parecen.
 *
 * <p><b>QUÉ CLASE DE TECHO ES, DICHO SIN ADORNOS.</b> Es de los que evitan absurdos, no de los
 * que cazan tipeos. El techo de cantidad de mostrador sí caza tipeos: 999,999 rechaza el 100
 * que iba a ser 1,00. Acá no se puede lograr lo mismo, porque los tipeos plausibles caen
 * adentro de cualquier rango usable: quien quiso poner 1,5 y puso 15, o quiso 10 y puso 100,
 * escribió un valor que el negocio podría querer de verdad. Ningún techo distingue eso, y
 * fingir que sí sería peor que decirlo.
 *
 * <h2>QUÉ ES Y QUÉ NO ES ESTA ANOTACIÓN</h2>
 *
 * <p>Igual que en {@link QuantityLimits} y {@link MoneyLimits}: es una <b>guarda de
 * contrato</b>, no la devolución que lee el operador. {@code GlobalExceptionHandler} manda los
 * mensajes de bean-validation bajo {@code details}, y aunque el interceptor del front ya los
 * enumera, el texto que el cajero lee en el camino normal sale de los validadores del
 * formulario. Lo que cubre esto es todo llamador que no pasa por el formulario: uso directo de
 * la API, el camino de sincronización offline y clientes futuros.
 *
 * <p>La fuente única del lado del front es {@code shared/forms/percentage-limits.ts}; estas
 * constantes la espejan porque {@code @DecimalMax} necesita literales de compilación.
 */
public final class PercentageLimits {

    private PercentageLimits() {}

    /**
     * Lo máximo que puede valer un recargo: 100%, o sea duplicar el precio.
     *
     * <p>Literal de cadena y no {@code double} porque {@code @DecimalMax} recibe una cadena y
     * {@code BigDecimal} se construye sin sorpresas de coma flotante desde una.
     */
    public static final String CAP = "100";

    /**
     * Lo mínimo que puede valer un descuento: -100%, el precio en cero.
     *
     * <p>Esta cota ya existía en el servicio y como CHECK de la base antes de esta clase; acá
     * se le da nombre para que las dos puntas del rango vivan juntas.
     */
    public static final String FLOOR = "-100";

    /** Tres dígitos enteros, los que necesita el 100. */
    public static final int INTEGER_DIGITS = 3;

    /**
     * Dos decimales, los mismos que dibuja la clase {@code porcentaje} del front.
     *
     * <p>La columna es {@code NUMERIC(9,4)} y se queda así. A diferencia de un importe, donde
     * los cuatro decimales guardan un cálculo intermedio, acá el valor lo TIPEA una persona y
     * nunca tuvo más de dos: la precisión de la columna es almacenamiento y esto es la regla.
     */
    public static final int FRACTION_DIGITS = 2;
}
