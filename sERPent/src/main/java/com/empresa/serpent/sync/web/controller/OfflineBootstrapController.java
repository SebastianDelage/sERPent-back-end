package com.empresa.serpent.sync.web.controller;

import com.empresa.serpent.sync.service.OfflineBootstrapService;
import com.empresa.serpent.sync.web.dto.response.OfflineBootstrapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class OfflineBootstrapController {

    private final OfflineBootstrapService service;

    @GetMapping("/bootstrap")
    public OfflineBootstrapResponse bootstrap() {
        return service.bootstrap();
    }
}