package com.empresa.serpent.shared.api;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        @PostMapping("/probe/validation")
        public void validation(@Valid @RequestBody ProbeBody body) {
            // Never reached: every field of the body sent by the test is invalid.
        }
    }

    /**
     * Tres campos cuyo ORDEN DE HASH NO COINCIDE CON EL ALFABÉTICO, y no es casualidad:
     * están elegidos para que el test pueda fallar.
     *
     * <p>La primera versión de este record usaba zocalo/alias/medio y PASABA IGUAL con un
     * HashMap: esos tres caen en cubetas que se recorren en orden alfabético, así que la
     * aserción no distinguía un mapa ordenado de uno que no lo estaba. Una aserción que no
     * puede fallar es peor que ninguna, porque se ve verde.
     *
     * <p>Con estos tres, el HashMap de la JDK los recorre codigo, alias, medio —cubetas 2, 9
     * y 13— contra el alfabético alias, codigo, medio. Verificado revirtiendo el TreeMap a
     * HashMap: el test falla.
     */
    record ProbeBody(
            @NotBlank(message = "El medio es obligatorio.") String medio,
            @NotBlank(message = "El alias es obligatorio.") String alias,
            @NotBlank(message = "El código es obligatorio.") String codigo
    ) {}

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

    /*
      LAS RAZONES DE UN RECHAZO VIENEN SIEMPRE EN EL MISMO ORDEN.

      Los mensajes de `details` son los que va a leer el operador en cuanto el interceptor
      del front deje de mirar solo `message`. Con un HashMap el orden sale de los hashes de
      las claves: el mismo rechazo puede listar sus motivos distinto en dos intentos
      seguidos, y esa es la clase de diferencia que nadie puede explicar y todos notan.

      SE AFIRMA EL ORDEN DEL JSON, NO EL CONTENIDO DEL MAPA. jsonPath puede decir qué claves
      hay pero no en qué orden vinieron, y el orden es justamente lo que se está fijando.
      Por eso se leen las claves del cuerpo crudo, en orden de aparición, y se comparan
      contra su propia versión ordenada.
    */
    @Test
    void validationErrors_comeBackInAStableOrder() throws Exception {
        String cuerpo = mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medio\":\"\",\"alias\":\"\",\"codigo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Revisá los datos ingresados."))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        List<String> enElJson = clavesDeDetails(cuerpo);

        assertThat(enElJson).containsExactly("alias", "codigo", "medio");
        assertThat(enElJson).isSorted();
    }

    /*
      Y el mismo pedido dos veces da lo mismo. Un HashMap con estas tres claves podría dar un
      orden estable dentro de una corrida y otro distinto en la siguiente JVM, así que esta
      afirmación sola no alcanzaría — va junto con la de arriba, que fija CUÁL es el orden.
    */
    @Test
    void theSameRejectionListsItsReasonsIdenticallyEveryTime() throws Exception {
        String primera = validationBody();
        String segunda = validationBody();

        assertThat(clavesDeDetails(primera)).isEqualTo(clavesDeDetails(segunda));
    }

    private String validationBody() throws Exception {
        return mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medio\":\"\",\"alias\":\"\",\"codigo\":\"\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Las claves de `details` en el orden en que aparecen en el JSON, no en el que las devuelva un Map. */
    private static List<String> clavesDeDetails(String json) {
        int desde = json.indexOf("\"details\"");
        assertThat(desde).as("el cuerpo trae un campo details").isGreaterThan(-1);

        String bloque = json.substring(json.indexOf('{', desde) + 1);
        bloque = bloque.substring(0, bloque.indexOf('}'));

        List<String> claves = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(bloque);
        while (m.find()) {
            claves.add(m.group(1));
        }
        return claves;
    }
}
