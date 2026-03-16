package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.PurchaseApplicationService;
import com.empresa.serpent.transactions.web.dto.request.CreatePurchaseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreatePurchaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseApplicationService purchaseApplicationService;

    @PostMapping
    public CreatePurchaseResponse create(@Valid @RequestBody CreatePurchaseRequest request) {
        return purchaseApplicationService.createPurchase(request);
    }
}