package com.empresa.serpent.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Orígenes permitidos por lista explícita, no por "*".
 *
 * <p>El default (localhost:4200, el dev server de Angular) alcanza para dev y para test sin
 * que ninguno de esos perfiles tenga que declarar la propiedad. El perfil prod la fija
 * mediante {@code serpent.cors.allowed-origins}, override de {@code CORS_ALLOWED_ORIGINS} —
 * ver application-prod.yml para por qué el origen real de Electron todavía no está acá.
 */
@Configuration
public class CorsConfig {

    @Value("${serpent.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return source;
    }
}