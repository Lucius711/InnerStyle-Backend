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
import com.innerstyle.wallet.gateway.MomoGateway;
import com.innerstyle.wallet.gateway.VnpayGateway;
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
    private VnpayGateway vnpayGateway;
    private MomoGateway momoGateway;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        printOrderRepository = mock(PrintOrderRepository.class);
        creditService = mock(CreditService.class);
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        paymentCallbackRepository = mock(PaymentCallbackRepository.class);
        vnpayGateway = mock(VnpayGateway.class);
        momoGateway = mock(MomoGateway.class);
        service = new PaymentServiceImpl(userRepository, mock(MembershipPlanRepository.class),
            printOrderRepository, creditService, paymentOrderRepository, paymentCallbackRepository,
            vnpayGateway, momoGateway, mock(PaymentProperties.class));
    }

    private PaymentOrder subscriptionOrder(PaymentStatus status) {
        PaymentOrder o = new PaymentOrder();
        o.setOrderCode("IS123");
        o.setAmount(new BigDecimal("100000"));
        o.setStatus(status);
        o.setPurpose(PaymentPurpose.SUBSCRIPTION);
        o.setReference("PRO");
        User u = new User();
        o.setUser(u);
        return o;
    }

    private GatewayVerification verification(boolean sig, boolean success, BigDecimal amount) {
        return new GatewayVerification(sig, success, "IS123", amount, "TXN1", "00");
    }

    // ------------------------------------------------------------------ VNPay IPN

    @Test
    @DisplayName("VNPay IPN: invalid signature → RspCode 97, never settled")
    void vnpayIpn_badSignature() {
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(false, true, new BigDecimal("100000")));

        Map<String, String> res = service.handleVnpayIpn(Map.of());

        assertThat(res.get("RspCode")).isEqualTo("97");
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("VNPay IPN: unknown order → RspCode 01")
    void vnpayIpn_unknownOrder() {
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.empty());

        assertThat(service.handleVnpayIpn(Map.of()).get("RspCode")).isEqualTo("01");
    }

    @Test
    @DisplayName("VNPay IPN: amount mismatch → RspCode 04, never settled")
    void vnpayIpn_amountMismatch() {
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("50000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.PENDING)));

        assertThat(service.handleVnpayIpn(Map.of()).get("RspCode")).isEqualTo("04");
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("VNPay IPN: already succeeded → RspCode 02 (idempotent)")
    void vnpayIpn_alreadyConfirmed() {
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.SUCCEEDED)));

        assertThat(service.handleVnpayIpn(Map.of()).get("RspCode")).isEqualTo("02");
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("VNPay IPN: valid success → RspCode 00, SUBSCRIPTION activates plan")
    void vnpayIpn_success_activatesPlan() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.of(order));

        assertThat(service.handleVnpayIpn(Map.of()).get("RspCode")).isEqualTo("00");
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(creditService).activatePlan(any(), eq("PRO"));
    }

    // ------------------------------------------------------------------ confirmReturn

    @Test
    @DisplayName("Return: valid success → SUCCESS, credited=true, plan activated")
    void return_success_credited() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.of(order));

        PaymentResultResponse res = service.confirmReturn(PaymentProvider.VNPAY, Map.of());

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.credited()).isTrue();
        verify(creditService).activatePlan(any(), eq("PRO"));
    }

    @Test
    @DisplayName("Return: already succeeded → SUCCESS but credited=false (idempotent)")
    void return_alreadySucceeded_notReCredited() {
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.SUCCEEDED)));

        PaymentResultResponse res = service.confirmReturn(PaymentProvider.VNPAY, Map.of());

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.credited()).isFalse();
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("Return: bad signature → FAILED, not credited")
    void return_badSignature_failed() {
        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(false, false, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.PENDING)));

        PaymentResultResponse res = service.confirmReturn(PaymentProvider.VNPAY, Map.of());

        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.credited()).isFalse();
    }

    // ------------------------------------------------------------------ MoMo IPN

    @Test
    @DisplayName("MoMo IPN: valid success → SUBSCRIPTION activates plan")
    void momoIpn_success_activatesPlan() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(momoGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.of(order));

        service.handleMomoIpn(Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(creditService).activatePlan(any(), eq("PRO"));
    }

    @Test
    @DisplayName("MoMo IPN: invalid signature → never settled")
    void momoIpn_badSignature_noSettle() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(momoGateway.verify(anyMap()))
            .thenReturn(verification(false, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.of(order));

        service.handleMomoIpn(Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("MoMo IPN: already succeeded → idempotent, not re-credited")
    void momoIpn_alreadySucceeded_notReCredited() {
        when(momoGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123"))
            .thenReturn(Optional.of(subscriptionOrder(PaymentStatus.SUCCEEDED)));

        service.handleMomoIpn(Map.of());

        verify(creditService, never()).activatePlan(any(), any());
    }

    @Test
    @DisplayName("MoMo IPN: amount mismatch → never settled")
    void momoIpn_amountMismatch_noSettle() {
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        when(momoGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("50000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.of(order));

        service.handleMomoIpn(Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(creditService, never()).activatePlan(any(), any());
    }

    // ------------------------------------------------------------------ PRINT fulfilment

    @Test
    @DisplayName("VNPay IPN: PRINT purpose success → print order marked PAID")
    void vnpayIpn_printPurpose_marksPrintOrderPaid() {
        UUID printOrderId = UUID.randomUUID();
        PaymentOrder order = subscriptionOrder(PaymentStatus.PENDING);
        order.setPurpose(PaymentPurpose.PRINT);
        order.setReference(printOrderId.toString());

        PrintOrder printOrder = new PrintOrder();
        printOrder.setStatus(PrintOrderStatus.PENDING);

        when(vnpayGateway.verify(anyMap()))
            .thenReturn(verification(true, true, new BigDecimal("100000")));
        when(paymentOrderRepository.findByOrderCodeForUpdate("IS123")).thenReturn(Optional.of(order));
        when(printOrderRepository.findById(printOrderId)).thenReturn(Optional.of(printOrder));

        assertThat(service.handleVnpayIpn(Map.of()).get("RspCode")).isEqualTo("00");
        assertThat(printOrder.getStatus()).isEqualTo(PrintOrderStatus.PAID);
        verify(printOrderRepository).save(printOrder);
        verify(creditService, never()).activatePlan(any(), any());
    }
}
