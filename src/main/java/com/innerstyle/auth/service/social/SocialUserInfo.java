package com.innerstyle.auth.service.social;

import com.innerstyle.auth.entity.enums.OauthProvider;

/**
 * Normalised identity extracted from a verified social provider token.
 */
public record SocialUserInfo(
    OauthProvider provider,
    String providerUserId,
    String email,
    String fullName,
    String avatarUrl
) {
}
