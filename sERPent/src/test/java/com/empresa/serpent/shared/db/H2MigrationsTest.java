package com.empresa.serpent.shared.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corre los juegos de H2 de punta a punta.
 *
 * <p>Hay dos combinaciones distintas en uso y las dos se prueban acá, porque un error de
 * sintaxis en cualquiera de las dos solo aparecería cuando alguien levanta algo:
 *
 * <ul>
 *   <li>{@code db/migration-h2} solo: es lo que carga el perfil de test, o sea el esquema y
 *       los datos de referencia, sin negocio de demostración.
 *   <li>{@code db/migration-h2} más {@code db/seed-dev}: es lo que carga el perfil de dev,
 *       con el negocio de mentira que hace que las pantallas se vean llenas.
 * </ul>
 *
 * <p>La segunda combinación no la ejerce ningún otro test —el perfil de test no la carga—,
 * así que sin esta clase el seed de demostración se rompería en silencio hasta que alguien
 * arrancara el servidor de desarrollo.
 */
class H2MigrationsTest {

    private static final String SCHEMA_AND_REFERENCE = "classpath:db/migration-h2";
    private static final String DEMO_SEED = "classpath:db/seed-dev";

    private DataSource freshDatabase(String name) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        // Same settings as application-dev.yml, so the migrations run under the same dialect.
        dataSource.setUrl("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private long count(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private MigrateResult migrate(DataSource dataSource, String... locations) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .load()
                .migrate();
    }

    @Test
    @DisplayName("El juego que carga el perfil de test se aplica limpio sobre una base vacía")
    void schemaAndReferenceApplyCleanly() {
        MigrateResult result = migrate(freshDatabase("migrations_apply"), SCHEMA_AND_REFERENCE);

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);
    }

    @Test
    @DisplayName("El juego que carga el perfil de dev, con el seed de demostración, también")
    void demoSeedAppliesCleanlyOnTop() {
        MigrateResult result =
                migrate(freshDatabase("migrations_apply_demo"), SCHEMA_AND_REFERENCE, DEMO_SEED);

        assertThat(result.success).isTrue();
        /*
          Estrictamente mayor que el juego sin demostración. Si el seed dejara de encontrarse
          —una carpeta mal escrita en locations, por ejemplo— Flyway no falla: aplica lo que
          encuentra y devuelve éxito. Comparar las cuentas es lo que distingue "corrió" de
          "no había nada que correr".
        */
        MigrateResult sinDemo =
                migrate(freshDatabase("migrations_apply_baseline"), SCHEMA_AND_REFERENCE);
        assertThat(result.migrationsExecuted).isGreaterThan(sinDemo.migrationsExecuted);
    }

    @Test
    @DisplayName("El perfil de dev tiene las dos carpetas en sus locations")
    void devProfileLoadsBothLocations() throws Exception {
        /*
          Lo de arriba prueba que las dos carpetas juntas se aplican bien, pero no que el
          perfil de dev las pida. Y si alguien saca db/seed-dev de esa linea, Flyway no
          protesta: aplica lo que encuentra y devuelve exito. El sintoma seria que quien
          levanta el proyecto ve las pantallas vacias, y recien ahi se enteraria.
        */
        String yaml;
        try (var in = getClass().getResourceAsStream("/application-dev.yml")) {
            yaml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        assertThat(yaml).contains("db/migration-h2");
        assertThat(yaml).contains("db/seed-dev");
    }

    @Test
    @DisplayName("El backfill de user_warehouses asigna cada depósito ACTIVO a cada usuario sembrado")
    void backfillCoversActiveWarehousesOnly() throws Exception {
        /*
          Con las dos carpetas: lo que este test mira son los usuarios y depósitos del negocio
          de demostración, que desde que se separó el seed no están en el juego del perfil de
          test. Sin la segunda carpeta las tres cuentas dan cero y el test pasaría en el vacío.
        */
        DataSource dataSource = freshDatabase("migrations_backfill");

        migrate(dataSource, SCHEMA_AND_REFERENCE, DEMO_SEED);

        long users = count(dataSource, "SELECT COUNT(*) FROM users");
        long activeWarehouses = count(dataSource, "SELECT COUNT(*) FROM warehouses WHERE active = TRUE");
        long inactiveWarehouses = count(dataSource, "SELECT COUNT(*) FROM warehouses WHERE active = FALSE");
        long assignments = count(dataSource, "SELECT COUNT(*) FROM user_warehouses");

        assertThat(users).isPositive();
        assertThat(activeWarehouses).isPositive();
        // El seed de demostración trae al menos un depósito inactivo, así que esto ejerce el
        // filtro de verdad en vez de pasar por vacío.
        assertThat(inactiveWarehouses).isPositive();
        // Conserva el comportamiento de hoy para los depósitos que estaban operativos; uno
        // inactivo nunca lo estuvo, así que no se backfillea.
        assertThat(assignments).isEqualTo(users * activeWarehouses);
    }
}
