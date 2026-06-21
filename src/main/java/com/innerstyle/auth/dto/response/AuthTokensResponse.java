package com.innerstyle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Issued credentials returned on login / refresh / social login.
 */
@Schema(description = "Access + refresh tokens with the authenticated profile")
public record AuthTokensResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    UserProfileResponse user
) {

    public static AuthTokensResponse of(String accessToken, long expiresInSeconds,
                                        String refreshToken, UserProfileResponse user) {
        return new AuthTokensResponse(accessToken, "Bearer", expiresInSeconds, refreshToken, user);
    }
}
