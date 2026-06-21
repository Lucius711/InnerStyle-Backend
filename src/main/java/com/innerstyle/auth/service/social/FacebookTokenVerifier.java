package com.innerstyle.auth.service.social;

import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Verifies a Facebook access token via the Graph API {@code /me} endpoint and returns the
 * user's basic profile.
 */
@Slf4j
@Component
public class FacebookTokenVerifier implements SocialTokenVerifier {

    private static final String GRAPH_ME_URL =
        "https://graph.facebook.com/v19.0/me?fields=id,name,email,picture";

    private final RestClient restClient = RestClient.create();

    @Override
    public OauthProvider provider() {
        return OauthProvider.FACEBOOK;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SocialUserInfo verify(String accessToken) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                .uri(GRAPH_ME_URL + "&access_token={t}", accessToken)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            log.debug("Facebook graph lookup failed: {}", ex.getMessage());
            throw new UnauthorizedException("auth.social.invalidToken");
        }
        if (body == null || body.get("id") == null) {
            throw new UnauthorizedException("auth.social.invalidToken");
        }
        return new SocialUserInfo(
            OauthProvider.FACEBOOK,
            String.valueOf(body.get("id")),
            asString(body.get("email")),
            asString(body.getOrDefault("name", body.get("id"))),
            extractPicture(body));
    }

    @SuppressWarnings("unchecked")
    private String extractPicture(Map<String, Object> body) {
        Object picture = body.get("picture");
        if (picture instanceof Map<?, ?> p && p.get("data") instanceof Map<?, ?> data) {
            return asString(data.get("url"));
        }
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
