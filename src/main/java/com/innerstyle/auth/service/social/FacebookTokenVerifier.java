package com.innerstyle.auth.service.social;

import com.innerstyle.auth.config.OauthProperties;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Verifies a Facebook access token via the Graph API {@code /me} endpoint and returns the
 * user's basic profile. Before trusting the token, its audience is checked against our own
 * app id via {@code /debug_token} (finding CAO-1: Graph {@code /me} alone only proves the token
 * is valid for *some* Facebook app, not for this one, letting a token issued to a different app
 * be replayed here).
 */
@Slf4j
@Component
public class FacebookTokenVerifier implements SocialTokenVerifier {

    private static final String GRAPH_ME_URL =
        "https://graph.facebook.com/v19.0/me?fields=id,name,email,picture";
    private static final String DEBUG_TOKEN_URL = "https://graph.facebook.com/v19.0/debug_token";
    private static final List<String> DEV_PROFILES = List.of("dev", "test", "local");

    private final OauthProperties props;
    private final Environment environment;
    private final RestClient restClient = RestClient.create();

    public FacebookTokenVerifier(OauthProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    /**
     * Whether this environment is allowed to tolerate a missing app-id/app-secret (dev/test/local,
     * or no profile at all — e.g. running the jar directly). Everywhere else, Facebook login must
     * be fully configured or it is refused outright (see {@link #verifyAudience}) rather than
     * silently trusting any Facebook token, which was the original bug (finding CAO-1).
     */
    private boolean devProfileActive() {
        return environment.getActiveProfiles().length == 0
            || Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> DEV_PROFILES.contains(p.toLowerCase()));
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.FACEBOOK;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SocialUserInfo verify(String accessToken) {
        verifyAudience(accessToken);
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

    /**
     * Confirms the token was issued to our own app before trusting the profile it unlocks.
     * Without app-id/app-secret configured we cannot perform this check at all: in dev/test we
     * fall back to the old trust-Graph-API-alone behavior (warned, opt-in risk for local setup);
     * everywhere else Facebook login is refused outright rather than accepting an unverifiable
     * token (finding CAO-1 — Graph {@code /me} alone only proves a token is valid for *some* app).
     */
    @SuppressWarnings("unchecked")
    private void verifyAudience(String accessToken) {
        String appId = props.facebook().appId();
        String appSecret = props.facebook().appSecret();
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            if (devProfileActive()) {
                log.warn("Facebook OAuth app-id/app-secret not configured; accepting this Facebook "
                    + "login without an audience check. INSECURE outside dev/test.");
                return;
            }
            throw new UnauthorizedException("auth.social.unconfigured");
        }
        String appAccessToken = appId + "|" + appSecret;
        Map<String, Object> response;
        try {
            response = restClient.get()
                .uri(DEBUG_TOKEN_URL + "?input_token={t}&access_token={a}", accessToken, appAccessToken)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            log.debug("Facebook debug_token check failed: {}", ex.getMessage());
            throw new UnauthorizedException("auth.social.invalidToken");
        }
        Object rawData = response == null ? null : response.get("data");
        Map<String, Object> data = rawData instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        boolean valid = data != null
            && Boolean.TRUE.equals(data.get("is_valid"))
            && appId.equals(String.valueOf(data.get("app_id")));
        if (!valid) {
            throw new UnauthorizedException("auth.social.invalidAudience");
        }
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
