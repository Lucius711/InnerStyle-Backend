package com.innerstyle.auth.service;

import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.enums.OauthProvider;

import java.util.UUID;

/**
 * Authentication &amp; account lifecycle. Sign-in is social-only (Google / Facebook); email +
 * password auth has been removed.
 */
public interface AuthService {

    AuthTokensResponse refresh(String refreshToken, String ip, String userAgent);

    void logout(String refreshToken, String accessToken);

    AuthTokensResponse socialLogin(OauthProvider provider, String providerToken, String ip, String userAgent);

    UserProfileResponse me(UUID userId);
}
