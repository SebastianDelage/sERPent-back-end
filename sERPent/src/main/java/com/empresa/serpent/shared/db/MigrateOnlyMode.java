package com.empresa.serpent.shared.db;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Migrates the database and exits, without serving anything.
 *
 * <p>LO USA EL INSTALADOR, y por eso existe. Flyway migra cuando arranca el backend, o sea
 * DESPUES de que el asistente dijo que todo salio bien. Si una migracion falla ahi, queda una
 * version nueva instalada sobre una base que no la acepta, y el unico que se entera es quien
 * abre la aplicacion al otro dia. Con este modo, el instalador migra EL MISMO, lee un codigo de
 * salida, y recien entonces dice que termino.
 *
 * <p>Es el mismo camino que usan los ERP de mercado: Odoo corre
 * {@code odoo-bin -u <modulo> --stop-after-init}, SAP Business One actualiza cada base con su
 * asistente y muestra el resultado, y Dynamics separa los binarios del {@code Start-NAVDataUpgrade}.
 * En los tres, la migracion es un paso explicito del tecnico y no un efecto de que alguien abra
 * la aplicacion. [RECUERDO: de entrenamiento, no verificado contra la documentacion.]
 *
 * <p>SIN SERVIDOR WEB, a proposito: asi no toma el puerto 8080 y no se pelea con una aplicacion
 * abierta, y ademas tarda menos. Lo que este modo NO prueba es que la aplicacion completa
 * levante; de eso se encarga el paso siguiente del instalador, que la arranca de verdad.
 *
 * <h2>Codigos de salida</h2>
 * <pre>
 *    0  migro, o no habia nada que migrar
 *   30  una migracion fallo
 *   31  no se pudo conectar a la base
 *   32  el backend no arranco por otro motivo (falta una variable, una propiedad cambio)
 * </pre>
 */
public final class MigrateOnlyMode {

    /** El argumento que lo pide. Va como propiedad de Spring para que no haya dos maneras. */
    private static final String FLAG = "--serpent.solo-migrar=true";

    /** La linea que lee el instalador. Prefijo fijo para poder buscarla en el log. */
    private static final String MARKER = "SERPENT-MIGRACION:";

    private MigrateOnlyMode() {
    }

    public static boolean requested(String[] args) {
        for (String arg : args) {
            if (FLAG.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the context just far enough to migrate, and returns the exit code.
     *
     * <p>Se atrapa {@code Throwable} y no {@code Exception}: un error de arranque puede llegar
     * como {@code NoClassDefFoundError} —una dependencia que no quedo en el jar, por ejemplo— y
     * eso tambien es "no arranco", no "fallo la migracion".
     */
    public static int run(Class<?> source, String[] args) {
        MigrationOutcome outcome;
        String detail;
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(source)
                .web(WebApplicationType.NONE)
                .run(args)) {
            outcome = MigrationOutcome.last();
            detail = MigrationOutcome.detail();
        } catch (Throwable t) {
            outcome = MigrationOutcome.last();
            detail = MigrationOutcome.detail();
            if (outcome == MigrationOutcome.MIGRATED || outcome == MigrationOutcome.NOT_ATTEMPTED) {
                // Migro bien (o ni llego a la base) y despues algo mas rompio el arranque: no es
                // un problema de migraciones, y el mensaje no puede decir que lo es.
                outcome = MigrationOutcome.NOT_ATTEMPTED;
                detail = shortMessage(t);
            }
        }
        // Una sola linea, con el resultado y el codigo crudo, para que el log del instalador la
        // tenga sin tener que interpretar un stack trace.
        System.out.println(MARKER + " resultado=" + outcome.name()
                + " codigo=" + outcome.exitCode()
                + " detalle=" + (detail.isEmpty() ? "(sin detalle)" : detail));
        return outcome.exitCode();
    }

    private static String shortMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return cause.getClass().getName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 400 ? message.substring(0, 400) + "..." : message;
    }
}
