package com.innerstyle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global API prefix (see rules/15-api-prefix-pattern.md).
 *
 * <p>
 * Adds a single {@code /api} base path to every {@code @RestController} so that
 * role/resource prefixes declared on the controllers (e.g. {@code /common/3d}) are served under
 * {@code /api/common/3d/...}. Swagger UI, actuator and other non-{@code @RestController}
 * endpoints are intentionally left untouched.
 *
 * <p><b>CORS is owned solely by {@code SecurityConfig#corsConfigurationSource()}</b> (a curated,
 * env-overridable origin allow-list). A previous MVC-level {@code addCorsMappings} override that
 * allowed <em>any</em> origin ({@code allowedOriginPatterns("*")}) has been removed — it silently
 * widened the allow-list and defeated the purpose of the security-layer configuration (BUG-001).
 */
@Configuration
public class ApiPrefixConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Exclude springdoc-openapi's own controllers (e.g. OpenApiWebMvcResource, which serves
        // /v3/api-docs) — they are @RestController too, so without this exclusion they'd
        // silently get prefixed to /api/v3/api-docs, breaking swagger-ui.html's default fetch of
        // /v3/api-docs (404 -> NoResourceFoundException). Keeps the doc comment's stated intent
        // that Swagger UI / API docs stay untouched by the /api prefix.
        configurer.addPathPrefix("/api",
                c -> c.isAnnotationPresent(RestController.class)
                        && !c.getPackageName().startsWith("org.springdoc"));
    }
}
