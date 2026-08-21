package com.empresa.serpent.cashcount.web.controller;

import com.empresa.serpent.cashcount.service.CashCountService;
import com.empresa.serpent.cashcount.service.ExpectedCashCountService;
import com.empresa.serpent.cashcount.web.dto.request.CreateCashCountRequest;
import com.empresa.serpent.cashcount.web.dto.response.CashCountResponse;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Closing the till and reading past closes.
 *
 * <p>No {@code @PreAuthorize} here: an employee closes their own branch, which is the whole
 * point of the feature. What they may see and touch is decided per branch — the write goes
 * through {@code WarehouseAccessService} and the reads through {@code WarehouseScopeService},
 * so an ADMIN sees every branch's history and an employee only their own.
 */
@RestController
@RequestMapping("/api/cash-counts")
@RequiredArgsConstructor
public class CashCountController {

    private final CashCountService cashCountService;
    private final ExpectedCashCountService expectedCashCountService;

    /**
     * What the till should hold right now, for the shift open at this branch.
     *
     * <p>{@code openingFloat} is echoed into the cash figures so the screen can show the
     * real expected amount as the cashier types it. It is not stored by this call, and the
     * close recomputes everything server-side anyway.
     */
    @GetMapping("/expected")
    public ExpectedCashCountResponse getExpected(
            @RequestParam Long warehouseId,
            @RequestParam(required = false, defaultValue = "0") BigDecimal openingFloat
    ) {
        return expectedCashCountService.getExpected(warehouseId, openingFloat);
    }

    @PostMapping
    public CashCountResponse create(@Valid @RequestBody CreateCashCountRequest request) {
        return cashCountService.create(request);
    }

    @GetMapping
    public Page<CashCountResponse> search(
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return cashCountService.search(warehouseId, pageable);
    }

    @GetMapping("/{id}")
    public CashCountResponse findById(@PathVariable Long id) {
        return cashCountService.findById(id);
    }
}
