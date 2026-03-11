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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private final TransactionRepository transactionRepository;

    public List<SalesByProductResponse> getSalesByProduct() {
        return transactionRepository.getSalesByProductReport();
    }

    public List<SalesDailyResponse> getSalesDaily() {
        return transactionRepository.getSalesDailyReportRaw()
                .stream()
                .map(row -> new SalesDailyResponse(
                        row.getDate(),
                        row.getTransactions(),
                        row.getTotalRevenue()
                ))
                .toList();
    }

    public List<SalesByPaymentMethodResponse> getSalesByPaymentMethod() {
        return transactionRepository.getSalesByPaymentMethodReport();
    }

    public SalesSummaryResponse getSalesSummary() {

        var row = transactionRepository.getSalesSummaryReportRaw();

        return new SalesSummaryResponse(
                row.getTransactions(),
                row.getTotalRevenue(),
                row.getAverageTicket() == null
                        ? null
                        : row.getAverageTicket().setScale(4, RoundingMode.HALF_UP)
        );
    }
}