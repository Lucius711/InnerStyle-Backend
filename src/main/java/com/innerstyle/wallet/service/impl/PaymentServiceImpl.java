package com.innerstyle.wallet.service.impl;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.membership.entity.MembershipPlan;
import com.innerstyle.membership.repository.MembershipPlanRepository;
import com.innerstyle.membership.service.CreditService;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.wallet.config.PaymentProperties;
import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.dto.response.PaymentResultResponse;
import com.innerstyle.wallet.entity.PaymentCallback;
import com.innerstyle.wallet.entity.PaymentOrder;
import com.innerstyle.wallet.entity.enums.PaymentCallbackKind;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.entity.enums.PaymentPurpose;
import com.innerstyle.wallet.entity.enums.PaymentStatus;
import com.innerstyle.wallet.gateway.GatewayVerification;
import com.innerstyle.wallet.gateway.PayosGateway;
import com.innerstyle.wallet.repository.PaymentCallbackRepository;
import com.innerstyle.wallet.repository.PaymentOrderRepository;
import com.innerstyle.wallet.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Direct payOS payments. On a verified success we fulfil the order: SUBSCRIPTION activates
 * the plan (grants credits); PRINT marks the print order paid. Idempotent by order status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final UserRepository userRepository;
    private final MembershipPlanRepository planRepository;
    private final PrintOrderRepository printOrderRepository;
    private final CreditService creditService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentCallbackRepository paymentCallbackRepository;
    private final PayosGateway payosGateway;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional
    public PaymentInitResponse createSubscriptionPayment(UUID userId, String planCode,
                                                         PaymentProvider provider, String clientIp) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        MembershipPlan plan = planRepository.findByCode(planCode)
            .orElseThrow(() -> new ResourceNotFoundException("membership.plan.notFound"));
        if (plan.getPrice() == null || plan.getPrice().signum() <= 0) {
            throw new BadRequestException("membership.plan.notPayable");
        }
        PaymentOrder order = newOrder(user, PaymentPurpose.SUBSCRIPTION, planCode, provider,
            plan.getPrice(), "Subscribe " + planCode);
        return buildPayUrl(order, clientIp);
    }

    @Override
    @Transactional
    public PaymentInitResponse createPrintPayment(UUID userId, UUID printOrderId, BigDecimal amount,
                                                  PaymentProvider provider, String clientIp) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        PaymentOrder order = newOrder(user, PaymentPurpose.PRINT, printOrderId.toString(), provider,
            amount, "3D print order " + printOrderId);
        return buildPayUrl(order, clientIp);
    }

    @Override
    @Transactional
    public void handlePayosWebhook(Map<String, Object> payload) {
        GatewayVerification v = payosGateway.verifyWebhook(payload);
        PaymentOrder order = lockOrder(v.orderCode());
        recordCallback(order, PaymentProvider.PAYOS, payload, v, PaymentCallbackKind.IPN);

        if (!v.signatureValid()) {
            log.warn("payOS webhook signature mismatch for order {}", v.orderCode());
            return;
        }
        if (order == null || order.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        if (v.amount() != null && order.getAmount().compareTo(v.amount()) != 0) {
            log.warn("payOS webhook amount mismatch for order {}", v.orderCode());
            return;
        }
        settle(order, v);
    }

    @Override
    @Transactional
    public PaymentResultResponse confirmReturn(PaymentProvider provider, Map<String, String> params) {
        GatewayVerification v = payosGateway.verifyReturn(params);
        PaymentOrder order = lockOrder(v.orderCode());
        recordCallback(order, provider, params, v, PaymentCallbackKind.RETURN);

        if (!v.signatureValid() || order == null) {
            return new PaymentResultResponse(v.orderCode(), "FAILED", v.amount(), false);
        }
        if (v.amount() == null || order.getAmount().compareTo(v.amount()) != 0) {
            return new PaymentResultResponse(order.getOrderCode(), "FAILED", order.getAmount(), false);
        }
        if (order.getStatus() == PaymentStatus.SUCCEEDED) {
            return new PaymentResultResponse(order.getOrderCode(), "SUCCESS", order.getAmount(), false);
        }
        if (v.success()) {
            settle(order, v);
            return new PaymentResultResponse(order.getOrderCode(), "SUCCESS", order.getAmount(), true);
        }
        order.setStatus(PaymentStatus.FAILED);
        paymentOrderRepository.save(order);
        return new PaymentResultResponse(order.getOrderCode(), "FAILED", order.getAmount(), false);
    }

    // --------------------------------------------------------------------- helpers

    /**
     * Load the order under a pessimistic write lock so concurrent / retried callbacks for the
     * same order serialize on the row and cannot both pass the SUCCEEDED check + settle (C1).
     * Returns null when the callback carries no / an unknown order code.
     */
    private PaymentOrder lockOrder(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return null;
        }
        return paymentOrderRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
    }

    private PaymentOrder newOrder(User user, PaymentPurpose purpose, String reference,
                                  PaymentProvider provider, BigDecimal amount, String description) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderCode(generateOrderCode());
        order.setUser(user);
        order.setPurpose(purpose);
        order.setReference(reference);
        order.setProvider(provider);
        order.setAmount(amount);
        order.setDescription(description);
        order.setStatus(PaymentStatus.PENDING);
        order.setExpiresAt(Instant.now().plus(paymentProperties.orderTtl()));
        return paymentOrderRepository.save(order);
    }

    private PaymentInitResponse buildPayUrl(PaymentOrder order, String clientIp) {
        PayosGateway.Checkout checkout = payosGateway.createPaymentLink(order);
        order.setStatus(PaymentStatus.PROCESSING);
        paymentOrderRepository.save(order);
        return new PaymentInitResponse(order.getOrderCode(), order.getProvider().name(),
            order.getAmount(), checkout.checkoutUrl(), checkout.qrCode());
    }

    /** Mark the order paid and fulfil what it funds. */
    private void settle(PaymentOrder order, GatewayVerification v) {
        order.setProviderTxnRef(v.providerTxnRef());
        if (!v.success()) {
            order.setStatus(PaymentStatus.FAILED);
            paymentOrderRepository.save(order);
            return;
        }
        order.setStatus(PaymentStatus.SUCCEEDED);
        order.setPaidAt(Instant.now());
        paymentOrderRepository.save(order);

        if (order.getPurpose() == PaymentPurpose.SUBSCRIPTION) {
            creditService.activatePlan(order.getUser().getId(), order.getReference());
        } else if (order.getPurpose() == PaymentPurpose.PRINT) {
            printOrderRepository.findById(UUID.fromString(order.getReference())).ifPresent(po -> {
                po.setStatus(PrintOrderStatus.PAID);
                printOrderRepository.save(po);
            });
        }
        log.info("Payment {} settled ({}, {})", order.getOrderCode(), order.getPurpose(),
            order.getReference());
    }

    private void recordCallback(PaymentOrder order, PaymentProvider provider,
                                Map<String, ?> params, GatewayVerification v,
                                PaymentCallbackKind kind) {
        PaymentCallback callback = new PaymentCallback();
        callback.setPaymentOrderId(order == null ? null : order.getId());
        callback.setProvider(provider);
        callback.setKind(kind);
        callback.setRawPayload(new LinkedHashMap<>(params));
        callback.setSignatureValid(v.signatureValid());
        callback.setResponseCode(v.responseCode());
        paymentCallbackRepository.save(callback);
    }

    private String generateOrderCode() {
        // payOS requires a numeric orderCode. Millisecond timestamp * 1000 + a 3-digit
        // tiebreaker keeps it well within a signed 64-bit long (and JS's safe-integer range).
        for (int i = 0; i < 5; i++) {
            String code = String.valueOf(System.currentTimeMillis() * 1000
                + ThreadLocalRandom.current().nextInt(1000));
            if (paymentOrderRepository.findByOrderCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BadRequestException("payment.orderCode.generationFailed");
    }
}
