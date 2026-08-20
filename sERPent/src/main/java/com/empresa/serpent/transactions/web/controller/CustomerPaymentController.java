package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.CustomerAccountService;
import com.empresa.serpent.transactions.service.CustomerPaymentService;
import com.empresa.serpent.transactions.web.dto.request.CreateCustomerPaymentRequest;
import com.empresa.serpent.transactions.web.dto.response.AccountStatementResponse;
import com.empresa.serpent.transactions.web.dto.response.CustomerPaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final CustomerPaymentService customerPaymentService;
    private final CustomerAccountService customerAccountService;

    @PostMapping("/api/customer-payments")
    public CustomerPaymentResponse create(@Valid @RequestBody CreateCustomerPaymentRequest request) {
        return customerPaymentService.create(request);
    }

    /**
     * Collections in a date range. Not revenue — the sales they settle already counted —
     * but the cash is in the drawer, so the day's count needs somewhere to see it.
     */
    @GetMapping("/api/customer-payments")
    public List<CustomerPaymentResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long paymentMethodId
    ) {
        return customerPaymentService.search(dateFrom, dateTo, paymentMethodId);
    }

    /** The customer's balance and every movement behind it. */
    @GetMapping("/api/customers/{id}/account")
    public AccountStatementResponse getAccount(@PathVariable Long id) {
        return customerAccountService.getStatement(id);
    }
}
