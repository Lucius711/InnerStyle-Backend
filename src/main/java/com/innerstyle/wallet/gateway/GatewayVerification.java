package com.innerstyle.wallet.gateway;

import java.math.BigDecimal;

/**
 * Outcome of verifying a gateway callback (IPN / return).
 *
 * @param signatureValid  the HMAC signature matched
 * @param success         the gateway reported a successful payment
 * @param orderCode       our merchant order reference
 * @param amount          the paid amount in major units (VND)
 * @param providerTxnRef  the gateway's own transaction id
 * @param responseCode    raw gateway response/result code
 */
public record GatewayVerification(
    boolean signatureValid,
    boolean success,
    String orderCode,
    BigDecimal amount,
    String providerTxnRef,
    String responseCode
) {
}
