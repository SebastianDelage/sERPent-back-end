package com.empresa.serpent.reports.service;

import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodReportResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.reports.web.dto.response.SalesSummaryResponse;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private final TransactionRepository transactionRepository;
    private final SaleRepository saleRepository;

    public List<SalesByProductResponse> getSalesByProduct(
            LocalDateTime dateFrom, LocalDateTime dateTo, Long warehouseId) {
        return transactionRepository.getSalesByProductReport(dateFrom, dateTo, warehouseId);
    }

    public List<SalesDailyResponse> getSalesDaily(
            LocalDateTime dateFrom, LocalDateTime dateTo, Long warehouseId) {
        return transactionRepository.getSalesDailyReportRaw(dateFrom, dateTo, warehouseId)
                .stream()
                .map(row -> new SalesDailyResponse(
                        row.getDate(),
                        row.getTransactions(),
                        row.getGrossSales(),
                        row.getReturnsTotal(),
                        row.getNetSales(),
                        // totalRevenue is an alias of netSales, kept for existing consumers.
                        row.getNetSales()
                ))
                .toList();
    }

    /**
     * What came in and how, plus what was sold on account and did not come in at all.
     *
     * <p>The two figures are gathered separately because they are different questions. A
     * credit sale names no payment method, so it cannot appear among the method rows, and
     * the total of those rows therefore stops being total sales — see
     * {@link SalesByPaymentMethodReportResponse} for the invariant this breaks and why.
     */
    public SalesByPaymentMethodReportResponse getSalesByPaymentMethod(
            LocalDateTime dateFrom, LocalDateTime dateTo, Long warehouseId) {

        List<SalesByPaymentMethodResponse> methods =
                transactionRepository.getSalesByPaymentMethodReport(dateFrom, dateTo, warehouseId);

        BigDecimal collected = methods.stream()
                .map(SalesByPaymentMethodResponse::totalRevenue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditSales = saleRepository.sumCreditSales(dateFrom, dateTo, warehouseId);

        return new SalesByPaymentMethodReportResponse(methods, collected, creditSales);
    }

    public SalesSummaryResponse getSalesSummary(
            LocalDateTime dateFrom, LocalDateTime dateTo, Long warehouseId) {

        var row = transactionRepository.getSalesSummaryReportRaw(dateFrom, dateTo, warehouseId);

        return new SalesSummaryResponse(
                row.getTransactions(),
                row.getListPriceSales(),
                row.getPaymentMethodSurcharges(),
                row.getManualAdjustments(),
                row.getReturnsTotal(),
                row.getNetSales(),
                // totalRevenue is an alias of netSales, kept for existing consumers.
                row.getNetSales(),
                row.getAverageTicket() == null
                        ? null
                        : row.getAverageTicket().setScale(4, RoundingMode.HALF_UP)
        );
    }
}