package com.innerstyle.wallet.service;

import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.dto.response.PaymentResultResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Direct payOS payments funding a subscription or a print order (no virtual wallet).
 */
public interface PaymentService {

    /** Start a payment to activate a subscription plan (PRO/MAX). */
    PaymentInitResponse createSubscriptionPayment(UUID userId, String planCode,
                                                  PaymentProvider provider, String clientIp);

    /** Start a payment for a print order. */
    PaymentInitResponse createPrintPayment(UUID userId, UUID printOrderId, BigDecimal amount,
                                           PaymentProvider provider, String clientIp);

    /** Handle a payOS webhook (server-to-server transaction notification). */
    void handlePayosWebhook(Map<String, Object> payload);

    /** Confirm a browser-return (verify + apply, idempotent). */
    PaymentResultResponse confirmReturn(PaymentProvider provider, Map<String, String> params);
}
