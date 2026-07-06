package com.innerstyle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Auth-flow settings bound from {@code app.auth.*}.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @DefaultValue("http://localhost:5173") String frontendBaseUrl,
    @DefaultValue("PT15M") Duration passwordResetTtl,
    @DefaultValue("5") int maxFailedLogins,
    @DefaultValue("PT15M") Duration lockDuration,
    // Email-verification OTP.
    @DefaultValue("6") int otpLength,
    @DefaultValue("PT10M") Duration otpTtl,
    @DefaultValue("5") int otpMaxAttempts,
    @DefaultValue("no-reply@innerstyle.app") String mailFrom,
    @DefaultValue("InnerStyle") String mailFromName
) {
}
