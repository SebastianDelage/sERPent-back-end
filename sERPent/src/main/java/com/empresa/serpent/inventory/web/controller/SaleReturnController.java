package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.SaleReturnApplicationService;
import com.empresa.serpent.inventory.web.dto.request.CreateSaleReturnRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateSaleReturnResponse;
import com.empresa.serpent.inventory.web.dto.response.SaleReturnResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sale-returns")
@RequiredArgsConstructor
public class SaleReturnController {

    private final SaleReturnApplicationService saleReturnApplicationService;

    @PostMapping
    public CreateSaleReturnResponse create(@Valid @RequestBody CreateSaleReturnRequest request) {
        return saleReturnApplicationService.createReturn(request);
    }

    @GetMapping("/sale/{saleId}")
    public List<SaleReturnResponse> findBySaleId(@PathVariable Long saleId) {
        return saleReturnApplicationService.findBySaleId(saleId);
    }
}