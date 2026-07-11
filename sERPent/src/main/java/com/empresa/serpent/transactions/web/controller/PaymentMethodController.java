package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.PaymentMethodService;
import com.empresa.serpent.transactions.web.dto.request.CreatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.response.PaymentMethodResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PostMapping
    public PaymentMethodResponse create(@Valid @RequestBody CreatePaymentMethodRequest request) {
        return paymentMethodService.create(request);
    }

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
    public List<PaymentMethodResponse> findAllActive() {
        return paymentMethodService.findAllActive();
    }

    @GetMapping("/all")
    public List<PaymentMethodResponse> findAll() {
        return paymentMethodService.findAll();
    }
}