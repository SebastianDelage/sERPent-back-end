package com.empresa.serpent.backup.web.controller;

import com.empresa.serpent.backup.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the screens need about backups: the state, and the "back up now" button.
 *
 * <p>No {@code @PreAuthorize}: whoever is standing at the till can ask for a backup and must be
 * able to see whether there is one. Nothing here exposes data, only when the last backup and the
 * last export happened.
 */
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /** Last backup, last export, free space: what the warnings are built on. */
    @GetMapping("/estado")
    public Map<String, Object> state() {
        return backupService.readState();
    }

    /**
     * Asks for a backup right now.
     *
     * <p>Contesta enseguida: el respaldo sigue por su cuenta. La pantalla se entera de como
     * termino mirando el estado, no esperando esta respuesta.
     */
    @PostMapping("/ahora")
    public Map<String, Object> backupNow() {
        boolean accepted = backupService.requestBackup("manual");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pedido", accepted);
        response.put("yaEstabaCorriendo", !accepted && backupService.isRunning());
        return response;
    }
}
