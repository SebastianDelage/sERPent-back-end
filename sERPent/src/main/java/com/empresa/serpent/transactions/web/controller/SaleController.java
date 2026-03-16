package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.SaleApplicationService;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateSaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleApplicationService saleApplicationService;

    @PostMapping
    public CreateSaleResponse create(@Valid @RequestBody CreateSaleRequest request) {
        return saleApplicationService.createSale(request);
    }
}