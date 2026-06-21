package com.innerstyle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Social-login provider settings bound from {@code app.oauth.*}. Used to validate the
 * audience of incoming provider tokens. Blank values skip the audience check (dev only).
 */
@ConfigurationProperties(prefix = "app.oauth")
public record OauthProperties(
    @DefaultValue Google google,
    @DefaultValue Facebook facebook
) {

    public record Google(@DefaultValue("") String clientId) {
    }

    public record Facebook(@DefaultValue("") String appId) {
    }
}
