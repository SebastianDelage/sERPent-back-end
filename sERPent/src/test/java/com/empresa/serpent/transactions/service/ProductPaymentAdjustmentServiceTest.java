package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.web.dto.request.CreateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.domain.entity.ProductPaymentAdjustmentEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.ProductPaymentAdjustmentRepository;
import com.empresa.serpent.transactions.web.dto.response.ProductPaymentAdjustmentResponse;
import com.empresa.serpent.transactions.web.mapper.ProductPaymentAdjustmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.empresa.serpent.support.TestEntityFactory.paymentMethod;
import static com.empresa.serpent.support.TestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    /**
     * LAS DOS PUNTAS DEL RANGO.
     *
     * <p>El piso ya se respetaba; el techo no existía. Hasta esta ronda un recargo no tenía
     * cota en ninguna capa y lo único que lo limitaba era el NUMERIC(9,4) de la columna, o sea
     * el 99.999,9999%. Estos casos existen para que volver a sacarlo cueste una prueba roja.
     *
     * <p>Los bordes se prueban en las dos direcciones y del lado de adentro también: un techo
     * que rechaza exactamente 100 sería tan bug como uno que acepta 200.
     */
    @Nested
    @DisplayName("The percentage range")
    class PercentageRange {

        private CreateProductPaymentAdjustmentRequest requestOf(String percentage) {
            return new CreateProductPaymentAdjustmentRequest(10L, 2L, new BigDecimal(percentage), true);
        }

        @Test
        @DisplayName("A surcharge over 100% is rejected: it would more than double the price")
        void rejectsSurchargeOverCap() {
            assertThatThrownBy(() -> service.create(requestOf("100.01")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("recargo")
                    .hasMessageContaining("100%");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("The typo this ceiling exists for: 200 where 20 belonged")
        void rejectsTheTypo() {
            assertThatThrownBy(() -> service.create(requestOf("200")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("A discount over 100% is rejected: it would drive the price below zero")
        void rejectsDiscountUnderFloor() {
            assertThatThrownBy(() -> service.create(requestOf("-100.01")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("descuento");
        }

        /*
          Los dos bordes exactos. Sin estos, un techo escrito con >= en vez de > pasaría todas
          las pruebas de arriba y rechazaría una configuración legítima.
        */
        @Test
        @DisplayName("Exactly 100 and exactly -100 are both accepted")
        void acceptsBothEdges() {
            given(productRepository.findById(10L)).willReturn(Optional.of(product(10L, "Pollo")));
            given(paymentMethodRepository.findById(2L)).willReturn(Optional.of(paymentMethod(2L, "Tarjeta")));
            given(repository.existsByProductIdAndPaymentMethodId(10L, 2L)).willReturn(false);

            assertThatCode(() -> service.create(requestOf("100"))).doesNotThrowAnyException();
            assertThatCode(() -> service.create(requestOf("-100"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A realistic card surcharge is nowhere near the ceiling")
        void acceptsRealisticSurcharge() {
            given(productRepository.findById(10L)).willReturn(Optional.of(product(10L, "Pollo")));
            given(paymentMethodRepository.findById(2L)).willReturn(Optional.of(paymentMethod(2L, "Tarjeta")));
            given(repository.existsByProductIdAndPaymentMethodId(10L, 2L)).willReturn(false);

            assertThatCode(() -> service.create(requestOf("30"))).doesNotThrowAnyException();
        }
    }
}
