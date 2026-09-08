package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QUÉ SE PRUEBA: el texto que sale de las anotaciones, RENDERIZADO, no el que está escrito.
 *
 * <p>Las anotaciones interpolan {@code {value}} para que el número no pueda vivir en dos
 * lugares, y eso tiene un filo: en {@code @DecimalMin} el valor es <b>-100</b>, así que un
 * mensaje redactado como "no puede superar el {value}%" sale diciendo "no puede superar el
 * -100%". Pasó en el primer intento de esta ronda. Leer el archivo fuente no lo habría
 * mostrado — hay que hacer correr el validador y mirar la cadena final.
 *
 * <p>Estas anotaciones son una guarda de contrato para los llamadores que no pasan por el
 * formulario: uso directo de la API y el camino de sincronización offline. El texto que el
 * cajero lee en el camino normal sale de los validadores del front.
 */
@DisplayName("The percentage range as the DTO enforces it")
class ProductPaymentAdjustmentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private List<String> messagesFor(String percentage) {
        return validator
                .validate(new CreateProductPaymentAdjustmentRequest(
                        10L, 2L, new BigDecimal(percentage), true))
                .stream()
                .map(v -> v.getMessage())
                .toList();
    }

    @Nested
    @DisplayName("what the message actually says")
    class RenderedText {

        @Test
        @DisplayName("The surcharge message names the ceiling and says what happens there")
        void surchargeMessageReadsRight() {
            assertThat(messagesFor("200"))
                    .containsExactly("Un recargo no puede superar el 100% del precio, que ya lo duplica.");
        }

        /*
          EL CASO QUE SE ESCRIBIÓ MAL LA PRIMERA VEZ. El valor firmado se nombra como mínimo y
          no como "lo que no se puede superar", porque interpolado da un número negativo.
        */
        @Test
        @DisplayName("The discount message does not end up saying \"over -100%\"")
        void discountMessageDoesNotReadBackwards() {
            List<String> messages = messagesFor("-200");

            assertThat(messages).containsExactly(
                    "El porcentaje del ajuste no puede ser menor que -100: "
                            + "un descuento mayor dejaría el precio en negativo.");
            assertThat(messages.get(0)).doesNotContain("superar el -100");
        }

        @Test
        @DisplayName("Three decimals are rejected by the digits guard, in Spanish")
        void tooManyDecimals() {
            assertThat(messagesFor("10.123"))
                    .containsExactly(
                            "El porcentaje del ajuste admite hasta 3 enteros y 2 decimales.");
        }
    }

    @Nested
    @DisplayName("the edges")
    class Edges {

        @Test
        @DisplayName("Both ends of the range pass")
        void bothEndsPass() {
            assertThat(messagesFor("100")).isEmpty();
            assertThat(messagesFor("-100")).isEmpty();
        }

        @Test
        @DisplayName("A hair past either end does not")
        void justPastEitherEndFails() {
            assertThat(messagesFor("100.01")).isNotEmpty();
            assertThat(messagesFor("-100.01")).isNotEmpty();
        }

        @Test
        @DisplayName("A realistic card surcharge passes")
        void realisticSurchargePasses() {
            assertThat(messagesFor("20.50")).isEmpty();
        }
    }
}
