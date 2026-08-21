package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.TerminalService;
import com.empresa.serpent.inventory.web.dto.request.CreateTerminalRequest;
import com.empresa.serpent.inventory.web.dto.request.UpdateTerminalRequest;
import com.empresa.serpent.inventory.web.dto.response.TerminalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Creating, editing, and deactivating terminals is configuration: which machine registers
 * where. Owner's call, so those actions are ADMIN-only. Reading them is not: the topbar
 * warehouse/terminal selector needs it for EVERY authenticated user, the same way payment
 * method surcharges and per-warehouse minimums stay readable while their writes are ADMIN-only.
 */
@RestController
@RequestMapping("/api/terminals")
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalService terminalService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public TerminalResponse create(@Valid @RequestBody CreateTerminalRequest request) {
        return terminalService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public TerminalResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTerminalRequest request
    ) {
        return terminalService.update(id, request);
    }

    @GetMapping("/{id}")
    public TerminalResponse findById(@PathVariable Long id) {
        return terminalService.findById(id);
    }

    /**
     * Lists terminals. This is also the endpoint the frontend calls when pairing a machine:
     * the available terminals are simply the active ones.
     */
    @GetMapping
    public List<TerminalResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return terminalService.search(name, includeInactive);
    }
}
