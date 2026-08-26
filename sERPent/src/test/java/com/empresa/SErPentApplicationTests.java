package com.empresa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sin @ActiveProfiles("test") esta clase caía en el perfil por defecto — antes eso significaba
 * conectarse a la Postgres real de application.yml sin que nadie lo pidiera, exactamente el
 * accidente que el comentario de application-test.properties describe. Lo hace explícito,
 * como las demás clases @SpringBootTest de esta suite.
 */
@SpringBootTest
@ActiveProfiles("test")
class sERPentApplicationTests {

    @Test
    void contextLoads() {
    }

}
