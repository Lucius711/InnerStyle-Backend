package com.innerstyle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global API prefix (see rules/15-api-prefix-pattern.md).
 *
 * <p>
 * Adds a single {@code /api} base path to every {@code @RestController} so that
 * role/resource
 * prefixes declared on the controllers (e.g. {@code /common/3d}) are served
 * under
 * {@code /api/common/3d/...}. Swagger UI, actuator and other
 * non-{@code @RestController}
 * endpoints are intentionally left untouched.
 */
@Configuration
public class ApiPrefixConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                c -> c.isAnnotationPresent(RestController.class));
    }

    /**
     * Dev-friendly CORS so the frontend (Vite dev server, or any origin) can call
     * the API
     * directly during development. Tighten {@code allowedOriginPatterns} for
     * production.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .maxAge(3600);
    }
}
