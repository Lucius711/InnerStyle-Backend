package com.innerstyle.auth.service.social;

import com.innerstyle.auth.config.OauthProperties;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Verifies a Google ID token via the tokeninfo endpoint and checks the audience against the
 * configured client id (when present). Suitable for tokens obtained by the Google client SDK.
 */
@Slf4j
@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final OauthProperties props;
    private final RestClient restClient = RestClient.create();

    public GoogleTokenVerifier(OauthProperties props) {
        this.props = props;
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.GOOGLE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SocialUserInfo verify(String idToken) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                .uri(TOKENINFO_URL + "?id_token={t}", idToken)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            log.debug("Google tokeninfo failed: {}", ex.getMessage());
            throw new UnauthorizedException("auth.social.invalidToken");
        }
        if (body == null || body.get("sub") == null) {
            throw new UnauthorizedException("auth.social.invalidToken");
        }
        String expectedAud = props.google().clientId();
        if (expectedAud != null && !expectedAud.isBlank()
            && !expectedAud.equals(String.valueOf(body.get("aud")))) {
            throw new UnauthorizedException("auth.social.invalidAudience");
        }
        return new SocialUserInfo(
            OauthProvider.GOOGLE,
            String.valueOf(body.get("sub")),
            asString(body.get("email")),
            asString(body.getOrDefault("name", body.get("email"))),
            asString(body.get("picture")));
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
