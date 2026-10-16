package com.innerstyle.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Customer email notifications ({@code app.notification.*}), sent through Resend's SMTP relay
 * ({@code spring.mail.*}). Emails are skipped while {@code RESEND_API_KEY} is empty (local dev).
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
    @DefaultValue("") String resendApiKey,
    @DefaultValue("InnerStyle <no-reply@innerstyle.online>") String mailFrom,
    @DefaultValue("http://localhost:5173") String frontendBaseUrl
) {
    public boolean enabled() {
        return resendApiKey != null && !resendApiKey.isBlank();
    }
}
