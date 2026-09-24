package com.innerstyle.wallet.service;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.membership.repository.MembershipPlanRepository;
import com.innerstyle.membership.service.CreditService;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.wallet.config.PaymentProperties;
import com.innerstyle.wallet.dto.response.PaymentResultResponse;
import com.innerstyle.wallet.entity.PaymentOrder;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.entity.enums.PaymentPurpose;
import com.innerstyle.wallet.entity.enums.PaymentStatus;
import com.innerstyle.wallet.gateway.GatewayVerification;
import com.innerstyle.wallet.gateway.PayosGateway;
import com.innerstyle.wallet.repository.PaymentCallbackRepository;
import com.innerstyle.wallet.repository.PaymentOrderRepository;
import com.innerstyle.wallet.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl}: gateway callback verification (signature, amount,
 * idempotency) and fulfilment on a verified success. Money must never be settled on an invalid
 * signature or a mismatched amount.
 */
class PaymentServiceImplTest {

    private UserRepository userRepository;
    private PrintOrderRepository printOrderRepository;
    private CreditService creditService;
    private PaymentOrderRepository paymentOrderRepository;
    private PaymentCallbackRepository paymentCallbackRepository;
    private PayosGateway payosGateway;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        printOrderRepository = mock(PrintOrderRepository.class);
        creditService = mock(CreditService.class);
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        paymentCallbackRepository = mock(PaymentCallbackRepository.class);
        payosGateway = mock(PayosGateway.class);
        service = new PaymentServiceImpl(userRepository, mock(MembershipPlanRepository.class),
            printOrderRepository, creditService, paymentOrderRepository, paymentCallbackRepository,
            payosGateway, mock(PaymentProperties.class));
    }

    private PaymentOrder subscriptionOrder(PaymentStatus status) {
        PaymentOrder o = new PaymentOrder();
        o.setOrderCode("123456");
        o.setAmount(new BigDecimal("100000"));
        o.setStatus(status);
        o.setPurpose(PaymentPurpose.SUBSCRIPTION);
        o.setReference("PRO");
        User u = new User();
        o.setUser(u);
        return o;
    }

    private GatewayVerification verification(boolean sig, boolean success, BigDecimal amount) {
        return new GatewayVerification(sig, success, "123456", amount, "TXN1", "00");
    }

    // ------------------------------------------------------------------ resume print payment

    @Test
    @DisplayName("resume print payment: reuses the existing payOS link, never creates a second one")
    void resumePrintPayment_reusesExistingLink() {
        UUID printOrderId = UUID.randomUUID();
        PaymentOrder existing = subscriptionOrder(PaymentStatus.PROCESSING);
        existing.setPurpose(PaymentPurpose.PRINT);
        existing.setReference(printOrderId.toString());
        existing.setProvider(PaymentProvider.PAYOS);
        existing.setCheckoutUrl("https://pay.payos.vn/web/abc");
        existing.setQrCode("QR");
        when(paymentOrderRepository.findFirstByPurposeAndReferenceAndStatusOrderByCreatedAtDesc(
            PaymentPurpose.PRINT, printOrderId.toString(), PaymentStatus.PROCESSING))
            .thenReturn(Optional.of(existing));

        var res = service.resumePrintPayment(UUID.randomUUID(), printOrderId, new BigDecimal("100000"), "1.1.1.1");

        assertThat(res.payUrl()).isEqualTo("https://pay.payos.vn/web/abc");
        assertThat(res.qrCode()).isEqualTo("QR");
        verify(payosGateway, never()).createPaymentLink(any());
    }

    // ------------------------------------------------------------------ payOS webhook

    @Test
    @DisplayName("payOS webhook: invalid signature → never settled")
    void payosWebhook_badSignature_noSettle() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(payosGateway.verifyWebhook(anyMap()))
            .thenReturn(verification(false, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456")).thenReturn(Optional.of(order));

        service.handlePayosWebhook(Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("payOS webhook: unknown order → no-op, no exception")
    void payosWebhook_unknownOrder() {
        when(payosGateway.verifyWebhook(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456")).thenReturn(Optional.empty());

        service.handlePayosWebhook(Map.of());

        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("payOS webhook: amount mismatch → never settled")
    void payosWebhook_amountMismatch_noSettle() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(payosGateway.verifyWebhook(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("50000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456")).thenReturn(Optional.of(order));

        service.handlePayosWebhook(Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("payOS webhook: already succeeded → idempotent, not re-credited")
    void payosWebhook_alreadyConfirmed() {
        when(payosGateway.verifyWebhook(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.SUCCEEDED)));

        service.handlePayosWebhook(Map.of());

        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("payOS webhook: valid success → SUBSCRIPTION activates plan")
    void payosWebhook_success_activatesPlan() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(payosGateway.verifyWebhook(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456")).thenReturn(Optional.of(order));

        service.handlePayosWebhook(Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(creditService).activatePlan(any(), eq("PRO"));
    }

    // ------------------------------------------------------------------ confirmReturn

    @Test
    @DisplayName("Return: valid success → SUCCESS, credited=true, plan activated")
    void return_success_credited() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(payosGateway.verifyReturn(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456")).thenReturn(Optional.of(order));

        PaymentResultResponse res = service.confirmReturn(PaymentProvider.PAYOS, Map.of());

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.credited()).isTrue();
        verify(creditService).activatePlan(any(), eq("PRO"));
    }

    @Test
    @DisplayName("Return: already succeeded → SUCCESS but credited=false (idempotent)")
    void return_alreadySucceeded_notReCredited() {
        when(payosGateway.verifyReturn(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.SUCCEEDED)));

        PaymentResultResponse res = service.confirmReturn(PaymentProvider.PAYOS, Map.of());

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.credited()).isFalse();
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("Return: bad signature → FAILED, not credited")
    void return_badSignature_failed() {
        when(payosGateway.verifyReturn(anyMap()))
            .thenReturn(verification(false, false, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.PENDING)));

        PaymentResultResponse res = service.confirmReturn(PaymentProvider.PAYOS, Map.of());

        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.credited()).isFalse();
    }

    // ------------------------------------------------------------------ PRINT fulfilment

    @Test
    @DisplayName("payOS webhook: PRINT purpose success → print order marked PAID")
    void payosWebhook_printPurpose_marksPrintOrderPaid() {
        UUID printOrderId = UUID.randomUUID();
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        order.setPurpose(PaymentPurpose.PRINT);
        order.setReference(printOrderId.toString());

        PrintOrder printOrder = new PrintOrder();
        printOrder.setStatus(PrintOrderStatus.PENDING);

        when(payosGateway.verifyWebhook(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("123456")).thenReturn(Optional.of(order));
        when(printOrderRepository.findById(printOrderId)).thenReturn(Optional.of(printOrder));

        service.handlePayosWebhook(Map.of());

        assertThat(printOrder.getStatus()).isEqualTo(PrintOrderStatus.PAID);
        verify(printOrderRepository).save(printOrder);
        verify(creditService, never()).activatePlan(any(), any());
    }
}
