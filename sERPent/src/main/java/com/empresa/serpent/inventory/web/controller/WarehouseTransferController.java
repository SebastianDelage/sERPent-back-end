package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.WarehouseTransferApplicationService;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseTransferRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateWarehouseTransferResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse-transfers")
@RequiredArgsConstructor
public class WarehouseTransferController {

    private final WarehouseTransferApplicationService warehouseTransferApplicationService;

    @PostMapping
    public CreateWarehouseTransferResponse create(@Valid @RequestBody CreateWarehouseTransferRequest request) {
        return warehouseTransferApplicationService.createTransfer(request);
    }
}