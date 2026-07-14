package com.innerstyle.auth.controller;

import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.dto.request.LoginRequest;
import com.innerstyle.auth.dto.request.RefreshTokenRequest;
import com.innerstyle.auth.dto.request.RegisterRequest;
import com.innerstyle.auth.dto.request.SocialLoginRequest;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (served under {@code /api/user/auth/**}).
 *
 * <p>Supports local sign-up / sign-in (email + password) as well as social sign-in
 * (Google / Facebook). No email-verification / OTP step is required for local accounts.
 *
 * <p>Finding M3: the long-lived refresh token is delivered as an HttpOnly, path-scoped cookie so
 * front-end JavaScript (and therefore any XSS) cannot read it. {@code /refresh} and {@code /logout}
 * read the token from that cookie, falling back to a request body for non-browser clients.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class AuthController {

    /** HttpOnly refresh cookie, scoped to the auth path so it is only sent where it is needed. */
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/user/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    /** Secure/SameSite are environment-driven so dev (http, Vite proxy) and prod (https) both work. */
    @Value("${app.auth.refresh-cookie.secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie.same-site:Lax}")
    private String refreshCookieSameSite;

    @Operation(summary = "Register a new account with email + password")
    @PostMapping("/register")
    public ApiResponse<AuthTokensResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        AuthTokensResponse tokens = authService.register(request.username(), request.password(),
                request.fullName(), clientIp(http), userAgent(http));
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success("auth.registered", tokens);
    }

    @Operation(summary = "Log in with email + password")
    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        AuthTokensResponse tokens = authService.login(request.username(), request.password(),
                clientIp(http), userAgent(http));
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success("auth.loggedIn", tokens);
    }

    @Operation(summary = "Exchange a refresh token (HttpOnly cookie or body) for a new access token")
    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(http, request);
        AuthTokensResponse tokens = authService.refresh(refreshToken, clientIp(http), userAgent(http));
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success("auth.refreshed", tokens);
    }

    @Operation(summary = "Log out (revoke the refresh token + blacklist the access token)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        String refreshToken = resolveRefreshToken(http, request);
        authService.logout(refreshToken, bearerToken(authHeader));
        clearRefreshCookie(response);
        return ApiResponse.success("auth.loggedOut");
    }

    @Operation(summary = "Log in / sign up with a social provider (google, facebook)")
    @PostMapping("/oauth/{provider}")
    public ApiResponse<AuthTokensResponse> socialLogin(@PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        OauthProvider parsed = parseProvider(provider);
        AuthTokensResponse tokens = authService.socialLogin(parsed, request.getToken(),
                clientIp(http), userAgent(http));
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.success("auth.loggedIn", tokens);
    }

    // --------------------------------------------------------------------- refresh cookie helpers

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(jwtProperties.refreshTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Prefer the HttpOnly cookie; fall back to a request body for non-browser clients. */
    private String resolveRefreshToken(HttpServletRequest http, RefreshTokenRequest body) {
        String fromCookie = readRefreshCookie(http);
        if (fromCookie != null && !fromCookie.isBlank()) {
            return fromCookie;
        }
        if (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank()) {
            return body.getRefreshToken();
        }
        throw new BadRequestException("1.refreshToken.required");
    }

    private String readRefreshCookie(HttpServletRequest http) {
        if (http.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : http.getCookies()) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String bearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length()).trim();
        }
        return null;
    }

    private OauthProvider parseProvider(String provider) {
        try {
            return OauthProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("auth.social.unsupportedProvider");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
