package com.empresa.serpent.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EL ARCHIVO DE CONFIGURACIÓN DE LA MÁQUINA, Y QUIÉN LE GANA A QUIÉN.
 *
 * <p>El perfil prod importa un archivo externo que escribe el instalador, con credenciales
 * generadas al azar. Estos tests fijan las tres cosas de las que depende que eso funcione, y que
 * hasta ahora no estaban escritas en ningún lado ejecutable:
 *
 * <ol>
 *   <li>que el archivo SE LEA — o sea, que la línea de spring.config.import siga estando;
 *   <li>que lo que venga de más arriba en el orden de Spring le GANE al archivo, que es lo que
 *       permite pisar la configuración en desarrollo sin tocar el archivo de la máquina;
 *   <li>que si el archivo NO ESTÁ, eso no sea un error en sí mismo — el "optional:" del import.
 *       Sin eso, el modo producción sin empaquetar dejaría de arrancar en la máquina de
 *       desarrollo, donde ese archivo no existe.
 * </ol>
 *
 * <p>SIN BASE DE DATOS: el runner arma un contexto vacío y solo se mira el Environment, así que
 * esto corre en el suite de siempre y tarda milisegundos.
 *
 * <p>QUÉ NO PRUEBA, y dónde está probado: que una VARIABLE DE ENTORNO le gane al archivo. Una
 * variable de entorno no se puede poner desde adentro de la JVM. Acá se usa una propiedad de
 * sistema, que en el orden de Spring está todavía más arriba que el entorno, así que cubre la
 * misma regla —lo de arriba gana— pero no el caso exacto. El caso exacto se midió contra el
 * backend real corriendo con PostgreSQL: con el archivo diciendo un usuario y la variable
 * diciendo otro, el error de autenticación nombró al de la variable. Está en docs/CONFIG_FILE.md.
 */
class ExternalConfigFileTest {

    private static final String FILE_USER = "usuario_del_archivo";
    private static final String FILE_SECRET = "claveDelArchivoLargaYAleatoriaDeAlMenos32Caracteres";

    @Test
    @DisplayName("el perfil prod lee las credenciales del archivo externo")
    void readsCredentialsFromTheExternalFile(@TempDir Path folder) throws Exception {
        Path file = writeConfigFile(folder);

        runWithConfigFile(file, context -> {
            assertThat(context.getEnvironment().getProperty("DB_USERNAME")).isEqualTo(FILE_USER);
            assertThat(context.getEnvironment().getProperty("JWT_SECRET")).isEqualTo(FILE_SECRET);

            // Y que además resuelva el placeholder, que es para lo que existe el archivo.
            assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                    .isEqualTo(FILE_USER);
        });
    }

    @Test
    @DisplayName("lo que viene de más arriba en el orden de Spring le gana al archivo")
    void anythingAboveConfigDataBeatsTheFile(@TempDir Path folder) throws Exception {
        Path file = writeConfigFile(folder);

        System.setProperty("DB_USERNAME", "usuario_de_mas_arriba");
        try {
            runWithConfigFile(file, context ->
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                            .isEqualTo("usuario_de_mas_arriba"));
        } finally {
            System.clearProperty("DB_USERNAME");
        }
    }

    @Test
    @DisplayName("que el archivo no exista no rompe el arranque por sí solo")
    void aMissingFileIsNotAnErrorByItself(@TempDir Path folder) {
        Path missing = folder.resolve("no-existe.properties");

        runWithConfigFile(missing, context -> {
            // El contexto levanta igual; lo que falta es el VALOR, no el archivo.
            assertThat(context.getEnvironment().getProperty("DB_USERNAME")).isNull();
        });
    }

    private Path writeConfigFile(Path folder) throws Exception {
        Path file = folder.resolve("serpent.properties");
        Files.writeString(file, """
                DB_USERNAME=%s
                DB_PASSWORD=clave_del_archivo
                JWT_SECRET=%s
                """.formatted(FILE_USER, FILE_SECRET));
        return file;
    }

    /**
     * Levanta un contexto vacío con el perfil prod y el import apuntado al archivo dado.
     *
     * <p>La ruta va por SERPENT_CONFIG_FILE, que es el mismo gancho que usa application-prod.yml
     * para poder apuntar el import a otro lado. Si ese gancho desapareciera, estos tests caen.
     */
    private void runWithConfigFile(Path file, Consumer<AssertableApplicationContext> assertions) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "SERPENT_CONFIG_FILE=" + file.toString().replace(java.io.File.separatorChar, '/'))
                .run(assertions::accept);
    }
}
