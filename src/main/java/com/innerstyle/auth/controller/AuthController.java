package com.innerstyle.auth.controller;

import com.innerstyle.auth.config.AuthProperties;
import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.config.OauthProperties;
import com.innerstyle.auth.dto.request.RefreshTokenRequest;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.auth.service.social.GoogleTokenVerifier;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.common.web.ClientIpResolver;
import com.innerstyle.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Public authentication endpoints (served under {@code /api/user/auth/**}).
 *
 * <p>Google sign-in runs entirely server-side (authorization-code flow): the browser is
 * redirected to {@code /oauth/google/authorize}, which sends it on to Google; Google redirects
 * back to {@code /oauth/google/callback} with a {@code code}, which is exchanged here (with the
 * confidential client secret) for an ID token, verified the same way a client-obtained token
 * always was, and turned into our own session. No Google JS SDK / client-side token ever exists.
 *
 * <p>Finding M3: the long-lived refresh token is delivered as an HttpOnly, path-scoped cookie so
 * front-end JavaScript (and therefore any XSS) cannot read it. {@code /refresh} and {@code /logout}
 * read the token from that cookie, falling back to a request body for non-browser clients.
 */
@Slf4j
@Tag(name = "Auth")
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class AuthController {

    /** HttpOnly refresh cookie, scoped to the auth path so it is only sent where it is needed. */
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/user/auth";
    /** Short-lived CSRF nonce for the Google redirect round-trip (state param). */
    private static final String STATE_COOKIE = "oauth_state";
    private static final String GOOGLE_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final OauthProperties oauthProperties;
    private final GoogleTokenVerifier googleTokenVerifier;

    /** Secure/SameSite are environment-driven so dev (http, Vite proxy) and prod (https) both work. */
    @Value("${app.auth.refresh-cookie.secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie.same-site:Lax}")
    private String refreshCookieSameSite;

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

    // --------------------------------------------------------------------- Google (server-side)

    @Operation(summary = "Start Google sign-in: redirects the browser to Google's consent screen")
    @GetMapping("/oauth/google/authorize")
    public void googleAuthorize(HttpServletResponse response) throws IOException {
        String state = randomState();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(STATE_COOKIE, state)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/user/auth/oauth/google")
                .maxAge(300)
                .build().toString());

        String url = GOOGLE_AUTHORIZE_URL
                + "?client_id=" + enc(oauthProperties.google().clientId())
                + "&redirect_uri=" + enc(oauthProperties.google().redirectUri())
                + "&response_type=code"
                + "&scope=" + enc("openid email profile")
                + "&state=" + enc(state);
        response.sendRedirect(url);
    }

    @Operation(summary = "Google redirects here with the authorization code")
    @GetMapping("/oauth/google/callback")
    public void googleCallback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest http, HttpServletResponse response) throws IOException {
        try {
            if (error != null || code == null || code.isBlank()) {
                throw new BadRequestException("auth.social.invalidToken");
            }
            String expectedState = readCookie(http, STATE_COOKIE);
            if (expectedState == null || !expectedState.equals(state)) {
                throw new BadRequestException("auth.social.invalidToken");
            }
            String idToken = googleTokenVerifier.exchangeCodeForIdToken(code);
            AuthTokensResponse tokens = authService.socialLogin(OauthProvider.GOOGLE, idToken,
                    clientIp(http), userAgent(http));
            setRefreshCookie(response, tokens.refreshToken());
            response.sendRedirect(authProperties.frontendBaseUrl()
                    + "/oauth/callback#access_token=" + enc(tokens.accessToken()));
        } catch (Exception ex) {
            log.warn("Google sign-in failed: {}", ex.getMessage());
            response.sendRedirect(authProperties.frontendBaseUrl() + "/login?error=google");
        }
    }

    private String randomState() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String readCookie(HttpServletRequest http, String name) {
        if (http.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : http.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
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
        return readCookie(http, REFRESH_COOKIE);
    }

    private String bearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length()).trim();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
