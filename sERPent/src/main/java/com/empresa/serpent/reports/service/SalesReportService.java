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

    public List<SalesByProductResponse> getSalesByProduct(LocalDateTime dateFrom, LocalDateTime dateTo) {
        return transactionRepository.getSalesByProductReport(dateFrom, dateTo);
    }

    public List<SalesDailyResponse> getSalesDaily(LocalDateTime dateFrom, LocalDateTime dateTo) {
        return transactionRepository.getSalesDailyReportRaw(dateFrom, dateTo)
                .stream()
                .map(row -> new SalesDailyResponse(
                        row.getDate(),
                        row.getTransactions(),
                        row.getTotalRevenue()
                ))
                .toList();
    }

    public List<SalesByPaymentMethodResponse> getSalesByPaymentMethod(LocalDateTime dateFrom, LocalDateTime dateTo) {
        return transactionRepository.getSalesByPaymentMethodReport(dateFrom, dateTo);
    }

    public SalesSummaryResponse getSalesSummary(LocalDateTime dateFrom, LocalDateTime dateTo) {

        var row = transactionRepository.getSalesSummaryReportRaw(dateFrom, dateTo);

        return new SalesSummaryResponse(
                row.getTransactions(),
                row.getTotalRevenue(),
                row.getAverageTicket() == null
                        ? null
                        : row.getAverageTicket().setScale(4, RoundingMode.HALF_UP)
        );
    }
}