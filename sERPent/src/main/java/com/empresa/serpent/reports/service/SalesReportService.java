package com.empresa.serpent.reports.service;

import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    }}