package com.innerstyle.auth.service.social;

import com.innerstyle.auth.entity.enums.OauthProvider;

/**
 * Verifies a provider-issued token (Google ID token / Facebook access token) and returns
 * the normalised user identity. Implementations are selected by {@link #provider()}.
 */
public interface SocialTokenVerifier {

    OauthProvider provider();

    SocialUserInfo verify(String token);
}
