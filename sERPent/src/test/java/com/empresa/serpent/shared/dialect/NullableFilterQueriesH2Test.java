package com.empresa.serpent.shared.dialect;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Las mismas consultas, contra H2, dentro del suite de siempre.
 *
 * <p>NO DETECTA EL BUG QUE MOTIVÓ ESTA CLASE: H2 acepta el parámetro binario y devuelve filas
 * como si nada. Está igual, y no es decorativa — tapa el otro agujero, el que no tiene nada que
 * ver con el motor: que estas consultas no las ejecutaba NADIE. Un nombre de propiedad mal
 * escrito, un JOIN que no compila, un alias que no existe: eso lo agarra acá, en cada
 * {@code ./mvnw test}, en vez de en el mostrador.
 *
 * <p>Lo que H2 no puede ver lo ve {@link NullableFilterQueriesPostgresTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class NullableFilterQueriesH2Test extends NullableFilterQueriesTest {
}
