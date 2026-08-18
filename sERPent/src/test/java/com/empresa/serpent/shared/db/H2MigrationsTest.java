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
 * Runs the H2 migration set end to end.
 *
 * <p>The test profile disables Flyway and lets Hibernate build the schema, so until this test
 * existed nothing executed {@code db/migration-h2} at all — a syntax error there would only
 * surface when a developer started the dev server. The dev database is the one every local
 * developer actually works against, so it deserves the same guarantee as production's.
 */
class H2MigrationsTest {

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

    @Test
    @DisplayName("The whole db/migration-h2 set applies cleanly on an empty database")
    void appliesCleanly() {
        DataSource dataSource = freshDatabase("migrations_apply");

        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-h2")
                .load()
                .migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);
    }

    @Test
    @DisplayName("The user_warehouses backfill assigns every ACTIVE warehouse to every seeded user")
    void backfillCoversActiveWarehousesOnly() throws Exception {
        DataSource dataSource = freshDatabase("migrations_backfill");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-h2")
                .load()
                .migrate();

        long users = count(dataSource, "SELECT COUNT(*) FROM users");
        long activeWarehouses = count(dataSource, "SELECT COUNT(*) FROM warehouses WHERE active = TRUE");
        long inactiveWarehouses = count(dataSource, "SELECT COUNT(*) FROM warehouses WHERE active = FALSE");
        long assignments = count(dataSource, "SELECT COUNT(*) FROM user_warehouses");

        assertThat(users).isPositive();
        assertThat(activeWarehouses).isPositive();
        // The seed data includes at least one inactive warehouse, so this actually exercises
        // the filter rather than passing vacuously.
        assertThat(inactiveWarehouses).isPositive();
        // Preserves today's behaviour for warehouses that were actually operable; an inactive
        // warehouse was never operable in the first place, so it is not backfilled.
        assertThat(assignments).isEqualTo(users * activeWarehouses);
    }
}
