package com.empresa.serpent.reports.service;

import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.reports.web.dto.response.SalesSummaryResponse;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private final TransactionRepository transactionRepository;

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

    public List<SalesByPaymentMethodResponse> getSalesByPaymentMethod(
            LocalDateTime dateFrom, LocalDateTime dateTo, Long warehouseId) {
        return transactionRepository.getSalesByPaymentMethodReport(dateFrom, dateTo, warehouseId);
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