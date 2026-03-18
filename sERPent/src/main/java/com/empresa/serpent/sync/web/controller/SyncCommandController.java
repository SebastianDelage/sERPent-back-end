package com.empresa.serpent.sync.web.controller;

import com.empresa.serpent.sync.service.SyncCommandApplicationService;
import com.empresa.serpent.sync.web.dto.request.SyncCommandRequest;
import com.empresa.serpent.sync.web.dto.response.SyncCommandResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync/commands")
@RequiredArgsConstructor
public class SyncCommandController {

    private final SyncCommandApplicationService service;

    @PostMapping
    public SyncCommandResponse sync(@Valid @RequestBody SyncCommandRequest request) {
        return service.process(request);
    }
}