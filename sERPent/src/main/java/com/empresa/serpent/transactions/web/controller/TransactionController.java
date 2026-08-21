package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.service.TransactionQueryService;
import com.empresa.serpent.transactions.web.dto.filter.TransactionFilter;
import com.empresa.serpent.transactions.web.dto.response.TransactionDetailResponse;
import com.empresa.serpent.transactions.web.dto.response.TransactionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    @GetMapping
    public Page<TransactionListResponse> search(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateTo,

            @RequestParam(required = false) Long createdByUserId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long paymentMethodId,
            @RequestParam(required = false) String text,

            @PageableDefault(size = 10, sort = "date", direction = DESC) Pageable pageable
    ) {
        TransactionFilter filter = new TransactionFilter(
                type,
                status,
                dateFrom,
                dateTo,
                createdByUserId,
                warehouseId,
                paymentMethodId,
                text
        );

        return transactionQueryService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    public TransactionDetailResponse findById(@PathVariable Long id) {
        return transactionQueryService.getById(id);
    }
}