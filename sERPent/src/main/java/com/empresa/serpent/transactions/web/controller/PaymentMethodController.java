package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.PaymentMethodService;
import com.empresa.serpent.transactions.web.dto.request.CreatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.response.PaymentMethodResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public PaymentMethodResponse create(@Valid @RequestBody CreatePaymentMethodRequest request) {
        return paymentMethodService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public PaymentMethodResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentMethodRequest request
    ) {
        return paymentMethodService.update(id, request);
    }

    @GetMapping("/{id}")
    public PaymentMethodResponse findById(@PathVariable Long id) {
        return paymentMethodService.findById(id);
    }

    @GetMapping
    public List<PaymentMethodResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return paymentMethodService.search(name, includeInactive);
    }
}