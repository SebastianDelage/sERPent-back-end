package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.SupplierAccountService;
import com.empresa.serpent.transactions.service.SupplierPaymentService;
import com.empresa.serpent.transactions.web.dto.request.CreateSupplierPaymentRequest;
import com.empresa.serpent.transactions.web.dto.response.AccountStatementResponse;
import com.empresa.serpent.transactions.web.dto.response.SupplierPaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;
    private final SupplierAccountService supplierAccountService;

    @PostMapping("/api/supplier-payments")
    public SupplierPaymentResponse create(@Valid @RequestBody CreateSupplierPaymentRequest request) {
        return supplierPaymentService.create(request);
    }

    /**
     * Payments in a date range. Not expenses — the purchases they settle already hit the
     * result — but the cash left, so the day's count needs somewhere to see it.
     */
    @GetMapping("/api/supplier-payments")
    public List<SupplierPaymentResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long paymentMethodId
    ) {
        return supplierPaymentService.search(dateFrom, dateTo, paymentMethodId);
    }

    /** The supplier's balance and every movement behind it. */
    @GetMapping("/api/suppliers/{id}/account")
    public AccountStatementResponse getAccount(@PathVariable Long id) {
        return supplierAccountService.getStatement(id);
    }
}
