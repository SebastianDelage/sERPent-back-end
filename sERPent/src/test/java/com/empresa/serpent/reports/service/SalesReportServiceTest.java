package com.empresa.serpent.reports.service;

import com.empresa.serpent.reports.repository.projection.SalesDailyProjection;
import com.empresa.serpent.reports.repository.projection.SalesSummaryProjection;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodReportResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.reports.web.dto.response.SalesSummaryResponse;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SaleRepository saleRepository;

    @InjectMocks
    private SalesReportService salesReportService;

    @Test
    @DisplayName("Should return sales by product report from repository")
    void shouldReturnSalesByProductReportFromRepository() {

        List<SalesByProductResponse> rows = List.of(
                new SalesByProductResponse(1L, "Pollo entero",
                        new BigDecimal("2.000"), new BigDecimal("1.000"),
                        new BigDecimal("9000.0000"), new BigDecimal("-4500.0000"),
                        new BigDecimal("4500.0000"), new BigDecimal("4500.0000")),
                new SalesByProductResponse(2L, "Pata muslo",
                        new BigDecimal("1.000"), BigDecimal.ZERO,
                        new BigDecimal("4600.0000"), BigDecimal.ZERO,
                        new BigDecimal("4600.0000"), new BigDecimal("4600.0000"))
        );

        given(transactionRepository.getSalesByProductReport(any(), any(), any())).willReturn(rows);

        List<SalesByProductResponse> result = salesReportService.getSalesByProduct(null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).quantitySold()).isEqualByComparingTo("2.000");
        assertThat(result.get(0).quantityReturned()).isEqualByComparingTo("1.000");
        assertThat(result.get(0).grossRevenue()).isEqualByComparingTo("9000.0000");
        assertThat(result.get(0).returnedAmount()).isEqualByComparingTo("-4500.0000");
        assertThat(result.get(0).netRevenue()).isEqualByComparingTo("4500.0000");

        assertThat(result.get(1).productId()).isEqualTo(2L);
        assertThat(result.get(1).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(1).quantitySold()).isEqualByComparingTo("1.000");
        assertThat(result.get(1).netRevenue()).isEqualByComparingTo("4600.0000");

        verify(transactionRepository).getSalesByProductReport(any(), any(), any());
    }

    @Test
    @DisplayName("Should map daily sales projection to response")
    void shouldMapDailySalesProjectionToResponse() {

        SalesDailyProjection row = dailyRow(
                LocalDate.of(2026, 3, 12), 1L, "9100.0000", "-1000.0000", "8100.0000");

        given(transactionRepository.getSalesDailyReportRaw(any(), any(), any())).willReturn(List.of(row));

        List<SalesDailyResponse> result = salesReportService.getSalesDaily(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(result.get(0).transactions()).isEqualTo(1L);
        assertThat(result.get(0).grossSales()).isEqualByComparingTo("9100.0000");
        assertThat(result.get(0).returns()).isEqualByComparingTo("-1000.0000");
        assertThat(result.get(0).netSales()).isEqualByComparingTo("8100.0000");
        // totalRevenue is an alias of netSales for existing consumers.
        assertThat(result.get(0).totalRevenue()).isEqualByComparingTo("8100.0000");

        verify(transactionRepository).getSalesDailyReportRaw(any(), any(), any());
    }

    @Test
    @DisplayName("Should return sales by payment method report from repository")
    void shouldReturnSalesByPaymentMethodReportFromRepository() {

        List<SalesByPaymentMethodResponse> rows = List.of(
                new SalesByPaymentMethodResponse(1L, "Cash", 1L, new BigDecimal("9100.0000"))
        );

        given(transactionRepository.getSalesByPaymentMethodReport(any(), any(), any())).willReturn(rows);
        given(saleRepository.sumCreditSales(any(), any(), any())).willReturn(new BigDecimal("2000.0000"));

        SalesByPaymentMethodReportResponse result =
                salesReportService.getSalesByPaymentMethod(null, null, null);

        assertThat(result.methods()).hasSize(1);
        assertThat(result.methods().get(0).paymentMethodId()).isEqualTo(1L);
        assertThat(result.methods().get(0).paymentMethodName()).isEqualTo("Cash");
        assertThat(result.methods().get(0).transactions()).isEqualTo(1L);
        assertThat(result.methods().get(0).totalRevenue()).isEqualByComparingTo("9100.0000");

        assertThat(result.collected()).isEqualByComparingTo("9100.0000");

        // Sold on account, so it is deliberately absent from the method rows and reported
        // on its own: the rows alone no longer add up to what was sold.
        assertThat(result.creditSales()).isEqualByComparingTo("2000.0000");

        verify(transactionRepository).getSalesByPaymentMethodReport(any(), any(), any());
    }

    @Test
    @DisplayName("Should map sales summary projection and normalize average ticket scale")
    void shouldMapSalesSummaryProjectionAndNormalizeAverageTicketScale() {

        SalesSummaryProjection row = summaryRow(3L, "100.0", "0", "0", "0", "100.0", "33.33333");

        given(transactionRepository.getSalesSummaryReportRaw(any(), any(), any())).willReturn(row);

        SalesSummaryResponse result = salesReportService.getSalesSummary(null, null, null);

        assertThat(result.transactions()).isEqualTo(3L);
        assertThat(result.totalRevenue()).isEqualByComparingTo("100.0");
        assertThat(result.averageTicket()).isEqualByComparingTo("33.3333");
        assertThat(result.averageTicket().scale()).isEqualTo(4);

        verify(transactionRepository).getSalesSummaryReportRaw(any(), any(), any());
    }

    @Test
    @DisplayName("Should carry every part of the breakdown through to the response")
    void shouldMapTheWholeBreakdown() {

        // 22500 at list price, +1500 surcharged by method, -2000 discounted manually,
        // 9000 returned: 22500 + 1500 - 2000 - 9000 = 13000.
        SalesSummaryProjection row = summaryRow(
                2L, "22500.0000", "1500.0000", "-2000.0000", "-9000.0000", "13000.0000", "11250.0000");

        given(transactionRepository.getSalesSummaryReportRaw(any(), any(), any())).willReturn(row);

        SalesSummaryResponse result = salesReportService.getSalesSummary(null, null, null);

        assertThat(result.listPriceSales()).isEqualByComparingTo("22500.0000");
        assertThat(result.paymentMethodSurcharges()).isEqualByComparingTo("1500.0000");
        assertThat(result.manualAdjustments()).isEqualByComparingTo("-2000.0000");
        assertThat(result.returns()).isEqualByComparingTo("-9000.0000");
        assertThat(result.netSales()).isEqualByComparingTo("13000.0000");

        // The parts add up, so the four lines explain the net rather than just sitting beside it.
        assertThat(
                result.listPriceSales()
                        .add(result.paymentMethodSurcharges())
                        .add(result.manualAdjustments())
                        .add(result.returns())
        ).isEqualByComparingTo(result.netSales());

        // totalRevenue mirrors netSales; averageTicket stays over sale totals.
        assertThat(result.totalRevenue()).isEqualByComparingTo("13000.0000");
        assertThat(result.averageTicket()).isEqualByComparingTo("11250.0000");
    }

    private static SalesSummaryProjection summaryRow(Long transactions,
                                                     String listPrice,
                                                     String surcharges,
                                                     String manualAdjustments,
                                                     String returns,
                                                     String net,
                                                     String averageTicket) {
        return new SalesSummaryProjection() {
            @Override
            public Long getTransactions() {
                return transactions;
            }

            @Override
            public BigDecimal getListPriceSales() {
                return new BigDecimal(listPrice);
            }

            @Override
            public BigDecimal getPaymentMethodSurcharges() {
                return new BigDecimal(surcharges);
            }

            @Override
            public BigDecimal getManualAdjustments() {
                return new BigDecimal(manualAdjustments);
            }

            @Override
            public BigDecimal getReturnsTotal() {
                return new BigDecimal(returns);
            }

            @Override
            public BigDecimal getNetSales() {
                return new BigDecimal(net);
            }

            @Override
            public BigDecimal getAverageTicket() {
                return new BigDecimal(averageTicket);
            }
        };
    }

    private static SalesDailyProjection dailyRow(LocalDate date,
                                                 Long transactions,
                                                 String gross,
                                                 String returns,
                                                 String net) {
        return new SalesDailyProjection() {
            @Override
            public LocalDate getDate() {
                return date;
            }

            @Override
            public Long getTransactions() {
                return transactions;
            }

            @Override
            public BigDecimal getGrossSales() {
                return new BigDecimal(gross);
            }

            @Override
            public BigDecimal getReturnsTotal() {
                return new BigDecimal(returns);
            }

            @Override
            public BigDecimal getNetSales() {
                return new BigDecimal(net);
            }
        };
    }
}