package com.innerstyle.auth.service;

import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.enums.OauthProvider;

import java.util.UUID;

/**
 * Authentication &amp; account lifecycle. Supports local (username + password) sign-up / sign-in
 * and social sign-in (Google / Facebook).
 */
public interface AuthService {

    AuthTokensResponse register(String username, String password, String fullName,
            String ip, String userAgent);

    AuthTokensResponse login(String username, String password, String ip, String userAgent);

    AuthTokensResponse refresh(String refreshToken, String ip, String userAgent);

    void logout(String refreshToken, String accessToken);

    AuthTokensResponse socialLogin(OauthProvider provider, String providerToken, String ip, String userAgent);

    UserProfileResponse me(UUID userId);
}
