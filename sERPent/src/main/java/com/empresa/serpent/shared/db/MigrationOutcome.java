package com.empresa.serpent.shared.db;

/**
 * How far the database migration got, so a failure can be told apart from a failure.
 *
 * <p>QUE PROBLEMA RESUELVE. El instalador de la fase 7 migra con el MISMO jar que usa la
 * aplicacion. Eso es a proposito —una segunda herramienta se desincroniza de la primera—, pero
 * trae una trampa: si el arranque falla, el codigo de salida por si solo no dice si fallo LA
 * MIGRACION o si el backend no llego ni a arrancar por un motivo que no tiene nada que ver
 * (falta una variable, la base esta caida, cambio una propiedad). Decirle al operador "fallo la
 * migracion" cuando lo que pasa es que la base no esta prendida lo manda a buscar donde no es.
 *
 * <p>POR QUE ESTADO ESTATICO, que normalmente seria un olor. Cuando el contexto de Spring falla
 * al arrancar, no hay contexto del cual leer un bean: el unico lugar donde esta informacion
 * sobrevive hasta el {@code main} es un campo estatico. Se escribe una sola vez, al principio,
 * desde un solo hilo, y se lee una sola vez, al final.
 */
public enum MigrationOutcome {

    /** No se llego a intentar: el arranque fallo antes de tocar la base. */
    NOT_ATTEMPTED(32),

    /** No se pudo abrir una conexion. La base esta caida, el puerto no es, la clave no entra. */
    NO_CONNECTION(31),

    /** Se conecto y una migracion fallo. Este SI es un problema de migraciones. */
    MIGRATION_FAILED(30),

    /** Migro, o no habia nada que migrar. */
    MIGRATED(0);

    /** El codigo con el que sale el modo "solo migrar". Numeros propios, no los de Spring. */
    private final int exitCode;

    private static volatile MigrationOutcome last = NOT_ATTEMPTED;
    private static volatile String detail = "";

    MigrationOutcome(int exitCode) {
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    public static void record(MigrationOutcome outcome, String why) {
        last = outcome;
        detail = why == null ? "" : why;
    }

    public static MigrationOutcome last() {
        return last;
    }

    public static String detail() {
        return detail;
    }
}
