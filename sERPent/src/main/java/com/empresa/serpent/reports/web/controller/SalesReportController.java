package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.reports.service.SalesReportService;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.reports.web.dto.response.SalesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports/sales")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;

    @GetMapping("/by-product")
    public List<SalesByProductResponse> getSalesByProduct() {
        return salesReportService.getSalesByProduct();
    }

    @GetMapping("/daily")
    public List<SalesDailyResponse> getSalesDaily() {
        return salesReportService.getSalesDaily();
    }

    @GetMapping("/by-payment-method")
    public List<SalesByPaymentMethodResponse> getSalesByPaymentMethod() {
        return salesReportService.getSalesByPaymentMethod();
    }

    @GetMapping("/summary")
    public SalesSummaryResponse getSalesSummary() {
        return salesReportService.getSalesSummary();
    }
}