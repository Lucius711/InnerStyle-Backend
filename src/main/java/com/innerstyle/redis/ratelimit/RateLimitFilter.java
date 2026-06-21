package com.innerstyle.redis.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.response.ErrorResponse;
import com.innerstyle.redis.RedisKeys;
import com.innerstyle.redis.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-bucket fixed-window rate limiting for the API. Buckets are chosen by path + method; the
 * identity is the authenticated user (USER scope) or the client IP (IP scope). Not a Spring
 * {@code @Component} on purpose — it is wired into the security chain by {@code SecurityConfig}
 * to avoid double servlet registration.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 3_600_000L;
    private static final long TEN_MIN_MS = 600_000L;

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties props;
    private final RedisKeys keys;
    private final ObjectMapper objectMapper;

    private enum Scope { IP, USER }

    private record Rule(String bucket, int limit, long windowMs, Scope scope) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!props.enabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        Rule rule = resolveRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        String id = rule.scope() == Scope.USER ? currentUserId(request) : clientIp(request);
        String key = keys.rateLimit(rule.bucket(), id);
        RateLimiterService.Decision decision = rateLimiter.check(key, rule.limit(), rule.windowMs());

        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                ErrorResponse.simple("rateLimit.exceeded",
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase()));
            return;
        }
        chain.doFilter(request, response);
    }

    private Rule resolveRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean post = "POST".equalsIgnoreCase(request.getMethod());

        // Server-to-server callbacks are not browser traffic; don't throttle them here.
        if (path.startsWith("/api/common/payments/") || path.startsWith("/api/webhooks/")) {
            return null;
        }
        if (path.contains("/auth/login")) {
            return new Rule("login", props.loginPerMinute(), MINUTE_MS, Scope.IP);
        }
        if (path.endsWith("/auth/register")) {
            return new Rule("register", props.registerPerHour(), HOUR_MS, Scope.IP);
        }
        if (path.contains("/auth/forgot-password") || path.contains("/auth/resend-verification")) {
            return new Rule("email", props.emailPer10min(), TEN_MIN_MS, Scope.IP);
        }
        if (post && path.startsWith("/api/common/3d/")) {
            return new Rule("generation", props.generationPerMinute(), MINUTE_MS, Scope.USER);
        }
        if (path.endsWith("/wallet/topup")) {
            return new Rule("payment", props.paymentPerMinute(), MINUTE_MS, Scope.USER);
        }
        if (path.startsWith("/api/")) {
            return new Rule("api", props.defaultPerMinute(), MINUTE_MS, Scope.IP);
        }
        return null;
    }

    private String currentUserId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return "u:" + principal.getId();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
