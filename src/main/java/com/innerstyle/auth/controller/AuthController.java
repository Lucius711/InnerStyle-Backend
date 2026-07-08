package com.innerstyle.auth.controller;

import com.innerstyle.auth.dto.request.RefreshTokenRequest;
import com.innerstyle.auth.dto.request.SocialLoginRequest;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (served under {@code /api/user/auth/**}).
 *
 * <p>Sign-in is social-only (Google / Facebook). Email + password auth has been removed; accounts
 * are created and linked via {@link #socialLogin}.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Exchange a refresh token for a new access token")
    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest http) {
        return ApiResponse.success("auth.refreshed",
                authService.refresh(request.getRefreshToken(), clientIp(http), userAgent(http)));
    }

    @Operation(summary = "Log out (revoke the refresh token + blacklist the access token)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken(), bearerToken(authHeader));
        return ApiResponse.success("auth.loggedOut");
    }

    @Operation(summary = "Log in / sign up with a social provider (google, facebook)")
    @PostMapping("/oauth/{provider}")
    public ApiResponse<AuthTokensResponse> socialLogin(@PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest http) {
        OauthProvider parsed = parseProvider(provider);
        return ApiResponse.success("auth.loggedIn",
                authService.socialLogin(parsed, request.getToken(), clientIp(http), userAgent(http)));
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
