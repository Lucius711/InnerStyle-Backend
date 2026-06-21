package com.innerstyle.wallet.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.wallet.dto.response.PaymentResultResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public gateway callback endpoints ({@code /api/common/payments/**}). These are called
 * server-to-server by the gateway, not by the browser, so they are intentionally unauthenticated
 * and rely on HMAC signature verification.
 */
@Tag(name = "Payments (gateway callbacks)")
@RestController
@RequestMapping("/common/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "VNPay IPN (server-to-server). Returns {RspCode, Message}.")
    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnpayIpn(params);
    }

    @Operation(summary = "MoMo IPN (server-to-server). Returns 204.")
    @PostMapping("/momo/ipn")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void momoIpn(@RequestBody Map<String, String> payload) {
        paymentService.handleMomoIpn(payload);
    }

    @Operation(summary = "Confirm a VNPay browser-return (verifies + credits idempotently)")
    @GetMapping("/vnpay/return")
    public ApiResponse<PaymentResultResponse> vnpayReturn(@RequestParam Map<String, String> params) {
        return ApiResponse.success("payment.return",
            paymentService.confirmReturn(PaymentProvider.VNPAY, params));
    }

    @Operation(summary = "Confirm a MoMo browser-return (verifies + credits idempotently)")
    @GetMapping("/momo/return")
    public ApiResponse<PaymentResultResponse> momoReturn(@RequestParam Map<String, String> params) {
        return ApiResponse.success("payment.return",
            paymentService.confirmReturn(PaymentProvider.MOMO, params));
    }
}
