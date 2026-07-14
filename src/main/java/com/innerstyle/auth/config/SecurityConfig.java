package com.innerstyle.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innerstyle.auth.security.JwtAuthenticationFilter;
import com.innerstyle.auth.security.RestAccessDeniedHandler;
import com.innerstyle.auth.security.RestAuthEntryPoint;
import com.innerstyle.redis.RedisKeys;
import com.innerstyle.redis.config.RateLimitProperties;
import com.innerstyle.redis.ratelimit.RateLimitFilter;
import com.innerstyle.redis.ratelimit.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security. Coarse path rules live here; fine-grained checks use
 * {@code @PreAuthorize} (method security is enabled). Public paths: auth
 * endpoints,
 * the existing public {@code /api/common/**} (Meshy generation + gallery),
 * webhooks,
 * Swagger and actuator. Everything else requires a valid access token.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final RedisKeys redisKeys;
    private final ObjectMapper objectMapper;

    private static final String[] PUBLIC_GET = {
            "/api/common/gallery/**"
    };

    private static final String[] PUBLIC_ANY = {
            "/api/*/auth/**",
            "/api/common/**",
            "/api/webhooks/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**"
    };

    /** BCrypt password hashing for local (email + password) accounts. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // The model + texture proxies stay public so the in-browser viewer can stream
                        // them
                        // (TextureLoader/GLTFLoader can't send an auth header).
                        .requestMatchers(HttpMethod.GET, "/api/common/3d/tasks/*/model/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/common/3d/tasks/*/texture").permitAll()
                        // Captured preview image, loaded by a plain <img> tag (no auth header
                        // possible).
                        .requestMatchers(HttpMethod.GET, "/api/common/3d/tasks/*/thumbnail").permitAll()
                        // NOTE: the browser-built USDZ upload (PUT .../usdz) is intentionally NOT
                        // public — it requires auth + ownership (finding M1) to stop anyone
                        // overwriting a task's AR asset. The subsequent GET .../model stays public.
                        // Everything else under /3d (create jobs + the private task library) requires
                        // auth.
                        .requestMatchers("/api/common/3d/**").authenticated()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(PUBLIC_ANY).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new RateLimitFilter(rateLimiterService, rateLimitProperties, redisKeys, objectMapper),
                        JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS allow-list. Uses origin *patterns* (wildcards allowed) so we can permit
     * every localhost port and any ngrok tunnel without listing each one. Because
     * {@code allowCredentials(true)} is set, the browser rejects a literal {@code *}
     * ACAO header — origin patterns make Spring echo back the concrete request
     * origin instead, which is what lets the in-browser 3D viewer read texture
     * pixels cross-origin (e.g. localhost:5173 -> backend) without tainting them.
     * Override via {@code APP_CORS_ALLOWED_ORIGIN_PATTERNS} (comma-separated) in prod.
     */
    @Value("${app.cors.allowed-origin-patterns}")
    private List<String> allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOriginPatterns);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
