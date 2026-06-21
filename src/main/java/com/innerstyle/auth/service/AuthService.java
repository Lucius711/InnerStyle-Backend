package com.innerstyle.auth.service;

import com.innerstyle.auth.dto.request.LoginRequest;
import com.innerstyle.auth.dto.request.RegisterRequest;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.enums.OauthProvider;

import java.util.UUID;

/**
 * Authentication & account lifecycle: registration, email verification, login/refresh/logout,
 * password reset and social login.
 */
public interface AuthService {

    UserProfileResponse register(RegisterRequest request);

    void verifyEmail(String token);

    void resendVerification(String email);

    AuthTokensResponse login(LoginRequest request, String ip, String userAgent);

    AuthTokensResponse refresh(String refreshToken, String ip, String userAgent);

    void logout(String refreshToken, String accessToken);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);

    AuthTokensResponse socialLogin(OauthProvider provider, String providerToken, String ip, String userAgent);

    UserProfileResponse me(UUID userId);
}
