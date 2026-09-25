package com.empresa.serpent.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the backup script and reads back what it recorded.
 *
 * <p>The backup itself lives in {@code ops/backup/backup-serpent-db.ps1}: one implementation,
 * shared by the till close, the "back up now" button, the scheduled net task and a technician
 * running it by hand. This service only launches it and reads its files.
 */
@Slf4j
@Service
public class BackupService {

    /** El respaldo no puede trabar el cierre de caja: se lanza y se contesta. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "serpent-backup");
        thread.setDaemon(true);
        return thread;
    });

    /** Un respaldo a la vez. El script tambien tiene su candado; esto evita encolar al pedo. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final boolean enabled;
    private final Path script;
    private final Path folder;
    private final Duration timeout;

    public BackupService(
            @Value("${serpent.backup.enabled:true}") boolean enabled,
            @Value("${serpent.backup.script:C:/Program Files/sERPent/ops/backup/backup-serpent-db.ps1}") String script,
            @Value("${serpent.backup.folder:C:/ProgramData/sERPent/respaldos}") String folder,
            @Value("${serpent.backup.timeout-minutes:30}") long timeoutMinutes) {
        this.enabled = enabled;
        this.script = Paths.get(script);
        this.folder = Paths.get(folder);
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    /**
     * Asks for a backup and returns immediately.
     *
     * @param origin what triggered it: {@code cierre-de-caja}, {@code manual} or {@code tarea}
     * @return false if one was already running, so the caller can say so
     */
    public boolean requestBackup(String origin) {
        if (!enabled) {
            log.info("Respaldo pedido ({}) pero esta apagado por configuracion.", origin);
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("Respaldo pedido ({}) pero ya hay uno corriendo.", origin);
            return false;
        }
        worker.submit(() -> {
            try {
                run(origin);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /** True while a backup is running, for the screen that asks. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * What the app needs to know, read from the files the script writes.
     *
     * <p>Comes from disk and never from the database: if the database is what was lost, a
     * record living inside it would be useless exactly when it is needed.
     */
    public Map<String, Object> readState() {
        Map<String, Object> state = new LinkedHashMap<>();
        Path file = folder.resolve("estado.json");
        try {
            if (Files.exists(file)) {
                String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
                parsed.forEach((key, value) -> state.put(String.valueOf(key), value));
            }
        } catch (IOException | RuntimeException e) {
            // Un estado.json ilegible no puede tumbar la pantalla: se dice que no se pudo leer.
            log.warn("No se pudo leer {}: {}", file, e.getMessage());
            state.put("estadoIlegible", true);
        }
        state.put("carpeta", folder.toString());
        state.put("respaldoCorriendo", running.get());
        state.put("respaldoHabilitado", enabled);
        return state;
    }

    private void run(String origin) {
        if (!Files.exists(script)) {
            log.error("No se encontro el script de respaldo en {}. El respaldo NO se hizo.", script);
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(
                System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
                "-Origen", origin);
        builder.redirectErrorStream(true);
        long started = System.currentTimeMillis();
        try {
            Process process = builder.start();
            String output = new String(readAll(process), StandardCharsets.UTF_8);
            if (!process.waitFor(timeout.toMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                log.error("El respaldo ({}) no termino en {} minutos y se corto.", origin, timeout.toMinutes());
                return;
            }
            int code = process.exitValue();
            long seconds = (System.currentTimeMillis() - started) / 1000;
            // El codigo crudo va al log SIEMPRE, salga bien o mal: 0 hecho, 11 no hacia falta,
            // 12 la base todavia no tiene tablas, 20 ya habia uno corriendo, 22 fallo pg_dump,
            // 23 no paso la verificacion.
            if (code == 0 || code == 11 || code == 12) {
                log.info("Respaldo ({}) termino con codigo {} en {} s.", origin, code, seconds);
            } else {
                log.error("Respaldo ({}) termino con codigo {} en {} s. Salida: {}", origin, code, seconds, output.trim());
            }
        } catch (IOException e) {
            log.error("No se pudo lanzar el respaldo ({}): {}", origin, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("El respaldo ({}) quedo interrumpido.", origin);
        }
    }

    private byte[] readAll(Process process) throws IOException {
        try (java.io.InputStream input = process.getInputStream();
             java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }
}
