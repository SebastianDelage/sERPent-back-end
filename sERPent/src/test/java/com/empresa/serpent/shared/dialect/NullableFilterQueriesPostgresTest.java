package com.empresa.serpent.shared.dialect;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Las mismas consultas, contra el PostgreSQL de verdad de esta máquina.
 *
 * <h2>Cómo se corre</h2>
 *
 * <pre>
 *   ./mvnw test -Dpgcheck=true
 * </pre>
 *
 * <p>Con PostgreSQL levantado y las credenciales en el entorno, en las variables
 * <b>PGCHECK_DB_USERNAME</b> y <b>PGCHECK_DB_PASSWORD</b> (ver application-pgcheck.properties,
 * que las prefiere sobre DB_USERNAME / DB_PASSWORD).
 *
 * <p>Y TIENEN QUE SER ÉSAS, no las de prod, aunque el perfil acepte las dos. Medido: con
 * DB_USERNAME puesta en el entorno, {@code ExternalConfigFileTest} falla dos casos en la misma
 * corrida — no por un bug, sino porque esa variable le gana al archivo de configuración que ese
 * test justamente verifica. El resultado era un suite en rojo por cómo se lo invocó, que es la
 * peor clase de rojo: el que enseña a ignorar los rojos.
 *
 * <h2>Por qué es a pedido y no parte del suite</h2>
 *
 * <p>Porque necesita una base que no está garantizada: en una máquina sin PostgreSQL —o sin la
 * base migrada— este test no puede correr, y un suite que falla por el entorno de quien lo corre
 * deja de significar algo. Lo correcto sería Testcontainers, que levanta un PostgreSQL efímero
 * y hace la pregunta innecesaria; hace falta Docker, que en esta máquina no está. Esto es el
 * reemplazo honesto mientras tanto: la misma verificación, disparada a mano.
 *
 * <h2>Por qué no puede romper la base</h2>
 *
 * <p>El antecedente existe y está escrito en application-test.properties: un {@code @SpringBootTest}
 * sin perfil se conectó una vez al PostgreSQL del desarrollador y hubo que reconstruir el
 * esquema. Este test SÍ se conecta a esa base a propósito, así que la garantía no puede ser
 * "apunta a otro lado" y tiene que ser otra:
 *
 * <ul>
 *   <li>{@code spring.flyway.enabled=false} — no migra, no crea, no versiona.
 *   <li>{@code ddl-auto=validate} — Hibernate compara y no toca. Nunca crea ni borra.
 *   <li>Todos los tests de la clase madre son SELECT. No hay un solo save, delete ni flush.
 * </ul>
 *
 * <p>De yapa, {@code validate} contra la base real verifica algo que hoy solo se verificaba
 * contra H2: que las entidades coincidan con el esquema que produjeron las migraciones de
 * PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("pgcheck")
@EnabledIfSystemProperty(named = "pgcheck", matches = "true",
        disabledReason = "Necesita PostgreSQL local; se pide con -Dpgcheck=true")
class NullableFilterQueriesPostgresTest extends NullableFilterQueriesTest {
}
