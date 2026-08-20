package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.reports.service.SalesReportService;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodReportResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.reports.web.dto.response.SalesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reports/sales")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;

    @GetMapping("/by-product")
    public List<SalesByProductResponse> getSalesByProduct(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long warehouseId
    ) {
        return salesReportService.getSalesByProduct(dateFrom, dateTo, warehouseId);
    }

    @GetMapping("/daily")
    public List<SalesDailyResponse> getSalesDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long warehouseId
    ) {
        return salesReportService.getSalesDaily(dateFrom, dateTo, warehouseId);
    }

    /**
     * Gross sales per payment method, plus what was sold on account and not collected.
     *
     * <p>Returns are not reflected here: a return is recorded without a payment method,
     * and assuming it was refunded through the original sale's method would be asserting a
     * fact the system does not have. Use /summary for figures net of returns.
     *
     * <p>The method rows no longer add up to total sales — credit sales have no method and
     * are reported separately in the same response. See
     * {@link SalesByPaymentMethodReportResponse}.
     */
    @GetMapping("/by-payment-method")
    public SalesByPaymentMethodReportResponse getSalesByPaymentMethod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long warehouseId
    ) {
        return salesReportService.getSalesByPaymentMethod(dateFrom, dateTo, warehouseId);
    }

    @GetMapping("/summary")
    public SalesSummaryResponse getSalesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long warehouseId
    ) {
        return salesReportService.getSalesSummary(dateFrom, dateTo, warehouseId);
    }
}