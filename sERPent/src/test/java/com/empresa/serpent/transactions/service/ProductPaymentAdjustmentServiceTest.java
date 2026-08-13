package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.transactions.domain.entity.ProductPaymentAdjustmentEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.ProductPaymentAdjustmentRepository;
import com.empresa.serpent.transactions.web.dto.response.ProductPaymentAdjustmentResponse;
import com.empresa.serpent.transactions.web.mapper.ProductPaymentAdjustmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.empresa.serpent.support.TestEntityFactory.paymentMethod;
import static com.empresa.serpent.support.TestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductPaymentAdjustmentServiceTest {

    @Mock
    private ProductPaymentAdjustmentRepository repository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private ProductPaymentAdjustmentMapper mapper;

    @InjectMocks
    private ProductPaymentAdjustmentService service;

    @Test
    @DisplayName("Should return the active rules for a payment method across several products in one query")
    void shouldFindRulesForPaymentMethodAndProducts() {
        ProductPaymentAdjustmentEntity rule = ProductPaymentAdjustmentEntity.builder()
                .id(1L)
                .product(product(10L, "Cigarrillos"))
                .paymentMethod(paymentMethod(2L, "Tarjeta"))
                .adjustmentPercentage(new BigDecimal("10"))
                .active(true)
                .build();

        ProductPaymentAdjustmentResponse response = new ProductPaymentAdjustmentResponse(
                1L, 10L, "Cigarrillos", 2L, "Tarjeta", new BigDecimal("10"), true);

        given(repository.findByPaymentMethodIdAndProductIdInAndActiveTrue(2L, List.of(10L, 20L)))
                .willReturn(List.of(rule));
        given(mapper.toResponseList(List.of(rule))).willReturn(List.of(response));

        List<ProductPaymentAdjustmentResponse> result =
                service.findByPaymentMethodAndProducts(2L, List.of(10L, 20L));

        assertThat(result).containsExactly(response);
        verify(repository).findByPaymentMethodIdAndProductIdInAndActiveTrue(2L, List.of(10L, 20L));
    }

    @Test
    @DisplayName("Should return an empty list when none of the cart's products have a rule for this method")
    void shouldReturnEmptyWhenNoRulesApply() {
        given(repository.findByPaymentMethodIdAndProductIdInAndActiveTrue(eq(2L), any()))
                .willReturn(List.of());
        given(mapper.toResponseList(List.of())).willReturn(List.of());

        List<ProductPaymentAdjustmentResponse> result =
                service.findByPaymentMethodAndProducts(2L, List.of(30L, 40L));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should not validate product or payment method existence: a stale id just yields no rule")
    void shouldNotValidateExistence() {
        given(repository.findByPaymentMethodIdAndProductIdInAndActiveTrue(eq(999L), any()))
                .willReturn(List.of());
        given(mapper.toResponseList(List.of())).willReturn(List.of());

        assertThatCode(() -> service.findByPaymentMethodAndProducts(999L, List.of(888L)))
                .doesNotThrowAnyException();
        verify(productRepository, never()).existsById(any());
        verify(paymentMethodRepository, never()).findById(any());
    }
}
