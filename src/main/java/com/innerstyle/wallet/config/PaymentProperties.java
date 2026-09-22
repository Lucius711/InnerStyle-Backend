package com.innerstyle.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Payment-gateway settings bound from {@code app.payment.*}. Secrets come from env vars.
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    @DefaultValue("PT15M") Duration orderTtl,
    @DefaultValue Payos payos
) {

    /** payOS (https://payos.vn) merchant configuration. */
    public record Payos(
        @DefaultValue("") String clientId,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String checksumKey,
        @DefaultValue("https://api-merchant.payos.vn") String endpoint,
        @DefaultValue("http://localhost:5173/wallet/payos-return") String returnUrl,
        @DefaultValue("http://localhost:5173/wallet/payos-return") String cancelUrl
    ) {
    }
}
