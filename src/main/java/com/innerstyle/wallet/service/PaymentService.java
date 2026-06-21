package com.innerstyle.wallet.service;

import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.dto.response.PaymentResultResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Direct VNPay / MoMo payments funding a subscription or a print order (no virtual wallet).
 */
public interface PaymentService {

    /** Start a payment to activate a subscription plan (PRO/MAX). */
    PaymentInitResponse createSubscriptionPayment(UUID userId, String planCode,
                                                  PaymentProvider provider, String clientIp);

    /** Start a payment for a print order. */
    PaymentInitResponse createPrintPayment(UUID userId, UUID printOrderId, BigDecimal amount,
                                           PaymentProvider provider, String clientIp);

    /** Handle a VNPay IPN; returns the {RspCode, Message} body VNPay expects. */
    Map<String, String> handleVnpayIpn(Map<String, String> params);

    /** Handle a MoMo IPN. */
    void handleMomoIpn(Map<String, String> params);

    /** Confirm a browser-return (verify + apply, idempotent). */
    PaymentResultResponse confirmReturn(PaymentProvider provider, Map<String, String> params);
}
