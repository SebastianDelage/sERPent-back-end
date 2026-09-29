package com.empresa.serpent.shared.db;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Wraps Flyway's migration so the reason a startup failed can be named.
 *
 * <p>NO CAMBIA LO QUE FLYWAY HACE: llama a {@code migrate()} igual que la estrategia por defecto
 * de Spring Boot, y vuelve a tirar cualquier excepcion tal cual. Lo unico que agrega es anotar
 * POR DONDE PASO, para que {@link MigrateOnlyMode} pueda distinguir "no se pudo conectar" de
 * "una migracion fallo".
 *
 * <p>LA CONEXION SE PIDE APARTE, ANTES, y esa parte es el truco. Si se dejara que
 * {@code migrate()} abra la conexion, una base apagada tiraria una excepcion de Flyway y
 * quedaria contada como "fallo una migracion", que es la respuesta correcta por el motivo
 * equivocado — y la que manda a alguien a revisar migraciones cuando lo que hay que hacer es
 * prender PostgreSQL.
 */
@Slf4j
@Configuration
public class MigrationRecordingConfig {

    @Bean
    public FlywayMigrationStrategy migrationStrategy() {
        return flyway -> {
            if (!canConnect(flyway)) {
                return;
            }
            try {
                flyway.migrate();
            } catch (RuntimeException e) {
                MigrationOutcome.record(MigrationOutcome.MIGRATION_FAILED, rootMessage(e));
                announce();
                throw e;
            }
            MigrationOutcome.record(MigrationOutcome.MIGRATED, "");
        };
    }

    /**
     * Prints the outcome on stdout, in one line, for whoever launched this process.
     *
     * <p>NO ES REDUNDANTE CON EL LOG. Lo leen dos programas distintos y ninguno de los dos puede
     * interpretar un stack trace: el instalador, cuando migra con {@code --serpent.solo-migrar};
     * y el shell de Electron, que captura la salida del backend y tiene que decidir QUE CARTEL
     * mostrar. Antes el shell adivinaba, y su cartel decia "la causa mas comun es que PostgreSQL
     * no este funcionando" cuando lo que habia fallado era una migracion y PostgreSQL andaba
     * perfecto: mandaba a revisar el servicio en vez de la migracion.
     *
     * <p>Una sola linea, con prefijo fijo, para que se pueda encontrar sin leer nada mas.
     */
    private void announce() {
        System.out.println("SERPENT-MIGRACION: resultado=" + MigrationOutcome.last().name()
                + " codigo=" + MigrationOutcome.last().exitCode()
                + " detalle=" + MigrationOutcome.detail());
    }

    /** True si se pudo abrir una conexion. Si no, anota el motivo y deja que Flyway reviente. */
    private boolean canConnect(Flyway flyway) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        } catch (Exception e) {
            MigrationOutcome.record(MigrationOutcome.NO_CONNECTION, rootMessage(e));
            log.error("No se pudo conectar a la base para migrar: {}", rootMessage(e));
            announce();
            // Se sigue igual: Flyway va a fallar solo, con su propio mensaje, y quien llama ya
            // sabe por que. Tragarse el error aca dejaria arrancar la app sin migrar.
            flyway.migrate();
            return false;
        }
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message.trim();
    }
}
