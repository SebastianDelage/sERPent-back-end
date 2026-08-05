package com.empresa.serpent.shared.api;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the sanitization guarantee the rest of the app relies on: a {@link NotFoundException} or
 * {@link IllegalArgumentException} thrown by any controller must never reach the client with its
 * raw, English, id-bearing message — only the generic Spanish text defined here. A regression in
 * {@link GlobalExceptionHandler} would silently leak technical detail to every REST endpoint.
 *
 * <p>Built with {@code standaloneSetup} instead of {@code @WebMvcTest} so this stays a plain unit
 * test of the advice's mapping behavior — no Spring context, no security filter chain, no JWT
 * beans required.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/not-found")
        public void notFound() {
            throw new NotFoundException("Product not found: 999");
        }

        @GetMapping("/probe/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("Source warehouse is inactive: 42");
        }

        @GetMapping("/probe/business")
        public void business() {
            throw new ValidationException("El depósito seleccionado está inactivo.");
        }
    }

    @Test
    void notFoundException_isSanitizedToGenericSpanishMessage() throws Exception {
        // Scoped to $.message, not the whole body: the body also carries a raw epoch
        // timestamp, whose digits can coincidentally contain "999" and fail this for
        // reasons that have nothing to do with sanitization.
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el recurso solicitado."))
                .andExpect(jsonPath("$.message", not(containsString("999"))))
                .andExpect(jsonPath("$.message", not(containsString("Product not found"))));
    }

    @Test
    void illegalArgumentException_isSanitizedToGenericSpanishMessage() throws Exception {
        // Scoped to $.message, not the whole body: the body also carries a raw epoch
        // timestamp, whose digits can coincidentally contain "42" and fail this for
        // reasons that have nothing to do with sanitization.
        mockMvc.perform(get("/probe/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud no es válida."))
                .andExpect(jsonPath("$.message", not(containsString("42"))))
                .andExpect(jsonPath("$.message", not(containsString("inactive"))));
    }

    @Test
    void businessException_passesThroughItsOwnCleanSpanishMessage() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El depósito seleccionado está inactivo."));
    }
}
