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
    @DefaultValue Vnpay vnpay,
    @DefaultValue Momo momo
) {

    /** VNPay (https://sandbox.vnpayment.vn) merchant configuration. */
    public record Vnpay(
        @DefaultValue("") String tmnCode,
        @DefaultValue("") String hashSecret,
        @DefaultValue("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html") String payUrl,
        @DefaultValue("2.1.0") String version,
        @DefaultValue("pay") String command,
        @DefaultValue("vn") String locale,
        @DefaultValue("http://localhost:5173/wallet/vnpay-return") String returnUrl
    ) {
    }

    /** MoMo (https://test-payment.momo.vn) partner configuration. */
    public record Momo(
        @DefaultValue("") String partnerCode,
        @DefaultValue("") String accessKey,
        @DefaultValue("") String secretKey,
        @DefaultValue("https://test-payment.momo.vn/v2/gateway/api/create") String endpoint,
        @DefaultValue("http://localhost:5173/wallet/momo-return") String redirectUrl,
        @DefaultValue("http://localhost:2207/api/common/payments/momo/ipn") String ipnUrl
    ) {
    }
}
