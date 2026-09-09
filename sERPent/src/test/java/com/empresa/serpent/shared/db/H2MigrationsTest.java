package com.empresa.serpent.shared.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>Desde que existe {@code db/common} hay una tercera carpeta y un supuesto nuevo que
 * confirmar: que lo de común se aplique después de todo lo viejo. Ver la clase anidada de
 * abajo y {@code db/common/README.md}.
 */
class H2MigrationsTest {

    private static final String SCHEMA_AND_REFERENCE = "classpath:db/migration-h2";
    private static final String DEMO_SEED = "classpath:db/seed-dev";
    private static final String COMMON = "classpath:db/common";

    /** El piso de versión de db/common. Ver db/common/README.md. */
    private static final int COMMON_VERSION_FLOOR = 40;

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
        String yaml = resource("/application-dev.yml");

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

    private String resource(String path) throws Exception {
        try (var in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("no se encontró " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * QUE db/common SE APLIQUE DESPUÉS DE TODO LO VIEJO, Y QUE LOS TRES PERFILES LA CARGUEN.
     *
     * <p>db/common es donde van las migraciones nuevas, escritas una sola vez para los dos
     * motores. El porqué de las tres carpetas está en db/common/README.md.
     *
     * <h2>POR QUÉ ACÁ HAY UNA MIGRACIÓN SONDA</h2>
     *
     * <p>Mientras db/common no tenga ningún .sql, afirmar "se aplica limpio" sería afirmar
     * nada: pasaría en verde con la carpeta vacía, con el nombre mal escrito en los locations,
     * o con la carpeta borrada. Flyway <b>no protesta ante un location que no existe</b>:
     * aplica lo que encuentra y devuelve éxito. Eso ya se comprobó en este proyecto, y es la
     * razón por la que el caso del seed de demostración compara cuentas en vez de mirar el
     * flag de éxito.
     *
     * <p>Así que estos casos plantan un V40 de verdad en una carpeta temporal, lo mezclan con
     * los juegos reales por {@code filesystem:} y comprueban el MECANISMO, que es lo que hoy se
     * puede comprobar. El día que haya una migración de verdad en db/common, el caso del piso
     * de versión empieza a mirarla a ella.
     *
     * <h2>SOLO H2</h2>
     *
     * <p>Que db/common corra igual en PostgreSQL no se verifica acá ni en ningún test: haría
     * falta Docker y Testcontainers. Esa validación es manual.
     */
    @Nested
    @DisplayName("db/common")
    class Common {

        @TempDir
        Path commonProbe;

        /** Un V40 real, en una carpeta aparte, para poder mezclarlo con los juegos de verdad. */
        private String probeLocation() throws Exception {
            String sql = "CREATE TABLE common_probe ("
                    + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);";
            Files.writeString(commonProbe.resolve("V40__common_probe.sql"), sql, StandardCharsets.UTF_8);
            return "filesystem:" + commonProbe.toAbsolutePath();
        }

        /**
         * Las versiones aplicadas, en el orden en que Flyway las aplicó.
         *
         * <p>Del resultado de la migración y no de flyway_schema_history: en H2 con
         * MODE=PostgreSQL esa tabla se crea con el nombre entrecomillado en minúscula, así que
         * un SELECT sin comillas no la encuentra. El resultado ya trae el orden de aplicación
         * y no depende de cómo cada motor pliegue las mayúsculas.
         */
        private List<String> appliedOrder(MigrateResult result) {
            List<String> versions = new ArrayList<>();
            result.migrations.forEach(m -> versions.add(m.version));
            return versions;
        }

        /*
          EL SUPUESTO QUE HABÍA QUE CONFIRMAR Y NO SUPONER: Flyway junta las carpetas de
          locations y ordena por NÚMERO DE VERSIÓN, no por carpeta ni por el orden de la lista.
          De eso depende todo el diseño — es lo único que garantiza que un V40 de common entre
          después del V21 de H2 y del V32 de Postgres sin que nadie tenga que acordarse.

          Y de paso: el SQL de la sonda usa las dos formas portables que el README manda usar,
          GENERATED BY DEFAULT AS IDENTITY y CURRENT_TIMESTAMP, así que si alguna dejara de
          funcionar en H2 se vería acá.
        */
        @Test
        @DisplayName("A V40 from common applies last, after every older migration")
        void commonAppliesAfterEverythingElse() throws Exception {
            DataSource dataSource = freshDatabase("common_order");

            MigrateResult result = migrate(dataSource, SCHEMA_AND_REFERENCE, probeLocation());

            assertThat(result.success).isTrue();

            List<String> applied = appliedOrder(result);
            assertThat(applied).contains("40");
            assertThat(applied.get(applied.size() - 1))
                    .as("la última en aplicarse tiene que ser la de common")
                    .isEqualTo("40");
        }

        /*
          Y el orden NO depende de cómo se listen las carpetas. Si dependiera, un perfil que las
          listara al revés aplicaría lo nuevo antes que lo viejo, y el error sería silencioso
          hasta que una migración de common tocara una tabla que todavía no existe.
        */
        @Test
        @DisplayName("The order comes from the version number, not from how locations are listed")
        void orderDoesNotDependOnTheListing() throws Exception {
            DataSource dataSource = freshDatabase("common_order_reversed");

            MigrateResult result = migrate(dataSource, probeLocation(), SCHEMA_AND_REFERENCE);

            assertThat(appliedOrder(result))
                    .last()
                    .as("common va al final aunque se liste primero")
                    .isEqualTo("40");
        }

        /** La combinación del perfil de dev, que es la que más carpetas mezcla. */
        @Test
        @DisplayName("Common applies on top of the dev combination too")
        void commonAppliesOnTopOfTheDevCombination() throws Exception {
            DataSource dataSource = freshDatabase("common_over_dev");

            MigrateResult result =
                    migrate(dataSource, SCHEMA_AND_REFERENCE, DEMO_SEED, probeLocation());

            assertThat(result.success).isTrue();
            assertThat(appliedOrder(result)).last().isEqualTo("40");
            assertThat(count(dataSource, "SELECT COUNT(*) FROM common_probe")).isZero();
        }

        /*
          LOS TRES PERFILES TIENEN QUE CARGARLA. Sin esto, una migración escrita en db/common no
          se aplicaría en algún perfil y nadie se enteraría: Flyway no falla, simplemente no la
          corre. Es el mismo agujero que ya se cerró con db/seed-dev y el perfil de dev.
        */
        @Test
        @DisplayName("All three profiles list db/common in their Flyway locations")
        void everyProfileLoadsCommon() throws Exception {
            for (String archivo : List.of(
                    "/application-dev.yml", "/application-prod.yml", "/application-test.properties")) {
                assertThat(flywayLocationsOf(archivo))
                        .as(archivo + " tiene que listar db/common en sus locations")
                        .contains("db/common");
            }
        }

        /**
         * El VALOR de la propiedad locations, no el archivo entero.
         *
         * <p>La primera versión de este caso buscaba "db/common" en el texto completo y pasaba
         * en verde con la carpeta sacada de los locations: el comentario que explica la
         * propiedad menciona db/common en prosa, y eso alcanzaba para satisfacer la aserción.
         * Se descubrió probándola contra el bug, que es la única forma en que se descubre.
         *
         * <p>Sirve para las dos sintaxis: "locations: ..." del YAML y "locations=..." del
         * .properties. Ignora las líneas comentadas.
         */
        private String flywayLocationsOf(String archivo) throws Exception {
            for (String linea : resource(archivo).lines().toList()) {
                String limpia = linea.trim();
                if (limpia.startsWith("#") || !limpia.replace(" ", "").contains("locations")) {
                    continue;
                }
                int corte = Math.max(limpia.indexOf(':'), limpia.indexOf('='));
                if (corte > 0) {
                    return limpia.substring(corte + 1).trim();
                }
            }
            return "";
        }

        /*
          Y que lo que efectivamente haya en db/common respete el piso. Una migración numerada
          por debajo de 40 podría chocar con un número ya usado —ahí Flyway falla al arrancar,
          que es ruidoso— o colarse en el medio de la historia vieja y aplicarse antes que la
          tabla que necesita, que no lo es. Con la carpeta todavía sin .sql este caso no afirma
          nada sobre archivos, pero empieza a hacerlo con el primero que se agregue.
        */
        /*
          LA CARPETA DE VERDAD, NO LA SONDA. Los casos de arriba prueban el mecanismo con un V40
          sintético; éste prueba que el db/common REAL aporta migraciones y que se aplican.

          Es la diferencia que se midió en producción: con la carpeta declarada y vacía, un
          arranque aplicó 32 migraciones y reportó éxito, exactamente igual que si la carpeta no
          estuviera. Comparar las cuentas —con common y sin common— es lo único que distingue
          "corrió" de "no había nada que correr", y es el mismo criterio que ya usa el caso del
          seed de demostración por la misma razón.
        */
        @Test
        @DisplayName("The real db/common contributes migrations and they actually apply")
        void theRealCommonFolderContributesMigrations() throws Exception {
            DataSource conCommon = freshDatabase("common_real");
            MigrateResult result = migrate(conCommon, SCHEMA_AND_REFERENCE, COMMON);

            assertThat(result.success).isTrue();

            MigrateResult sinCommon =
                    migrate(freshDatabase("common_real_baseline"), SCHEMA_AND_REFERENCE);
            assertThat(result.migrationsExecuted)
                    .as("db/common tiene que aportar migraciones, no cero")
                    .isGreaterThan(sinCommon.migrationsExecuted);

            // Y que lo que aportó haya corrido de verdad, no solo contado.
            assertThat(count(conCommon,
                    "SELECT COUNT(*) FROM schema_conventions WHERE name = 'common-migrations'"))
                    .as("la fila que deja V40 tiene que estar")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Whatever lands in db/common is numbered V40 or higher")
        void commonMigrationsRespectTheVersionFloor() throws Exception {
            Path carpeta = Path.of("src/main/resources/db/common");
            assertThat(Files.isDirectory(carpeta))
                    .as("db/common tiene que existir; si se borra, los locations quedan mudos")
                    .isTrue();

            try (var archivos = Files.list(carpeta)) {
                archivos.filter(f -> f.getFileName().toString().endsWith(".sql"))
                        .forEach(f -> {
                            String nombre = f.getFileName().toString();
                            int version = Integer.parseInt(nombre.substring(1, nombre.indexOf("__")));
                            assertThat(version)
                                    .as(nombre + " está por debajo del piso de db/common")
                                    .isGreaterThanOrEqualTo(COMMON_VERSION_FLOOR);
                        });
            }
        }
    }
}
