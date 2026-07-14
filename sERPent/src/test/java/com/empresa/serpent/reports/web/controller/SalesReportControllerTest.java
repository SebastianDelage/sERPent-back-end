package com.empresa.serpent.reports.web.controller;

import com.empresa.serpent.reports.service.SalesReportService;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.reports.web.dto.response.SalesDailyResponse;
import com.empresa.serpent.reports.web.dto.response.SalesSummaryResponse;
import com.empresa.serpent.shared.security.JwtAuthenticationFilter;
import com.empresa.serpent.shared.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SalesReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = { JwtAuthenticationFilter.class, JwtService.class }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SalesReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SalesReportService salesReportService;

    @Test
    @DisplayName("Should return sales by product report")
    void shouldReturnSalesByProductReport() throws Exception {

        List<SalesByProductResponse> response = List.of(
                new SalesByProductResponse(1L, "Pollo entero", new BigDecimal("1.000"), new BigDecimal("4500.0000")),
                new SalesByProductResponse(2L, "Pata muslo", new BigDecimal("1.000"), new BigDecimal("4600.0000"))
        );

        given(salesReportService.getSalesByProduct(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/reports/sales/by-product"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].productName").value("Pollo entero"))
                .andExpect(jsonPath("$[0].quantitySold").value(1.000))
                .andExpect(jsonPath("$[0].totalRevenue").value(4500.0000))
                .andExpect(jsonPath("$[1].productId").value(2))
                .andExpect(jsonPath("$[1].productName").value("Pata muslo"))
                .andExpect(jsonPath("$[1].quantitySold").value(1.000))
                .andExpect(jsonPath("$[1].totalRevenue").value(4600.0000));
    }

    @Test
    @DisplayName("Should return daily sales report")
    void shouldReturnDailySalesReport() throws Exception {

        List<SalesDailyResponse> response = List.of(
                new SalesDailyResponse(LocalDate.of(2026, 3, 12), 1L, new BigDecimal("9100.0000"))
        );

        given(salesReportService.getSalesDaily(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/reports/sales/daily"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].date").value("2026-03-12"))
                .andExpect(jsonPath("$[0].transactions").value(1))
                .andExpect(jsonPath("$[0].totalRevenue").value(9100.0000));
    }

    @Test
    @DisplayName("Should return sales by payment method report")
    void shouldReturnSalesByPaymentMethodReport() throws Exception {

        List<SalesByPaymentMethodResponse> response = List.of(
                new SalesByPaymentMethodResponse(1L, "Cash", 1L, new BigDecimal("9100.0000"))
        );

        given(salesReportService.getSalesByPaymentMethod(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/reports/sales/by-payment-method"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].paymentMethodId").value(1))
                .andExpect(jsonPath("$[0].paymentMethodName").value("Cash"))
                .andExpect(jsonPath("$[0].transactions").value(1))
                .andExpect(jsonPath("$[0].totalRevenue").value(9100.0000));
    }

    @Test
    @DisplayName("Should return sales summary report")
    void shouldReturnSalesSummaryReport() throws Exception {

        SalesSummaryResponse response = new SalesSummaryResponse(
                1L,
                new BigDecimal("9100.0000"),
                new BigDecimal("9100.0000")
        );

        given(salesReportService.getSalesSummary(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/reports/sales/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactions").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(9100.0000))
                .andExpect(jsonPath("$.averageTicket").value(9100.0000));
    }
}