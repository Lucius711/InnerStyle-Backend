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

    @Operation(summary = "payOS webhook (server-to-server). Returns 204.")
    @PostMapping("/payos/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void payosWebhook(@RequestBody Map<String, Object> payload) {
        paymentService.handlePayosWebhook(payload);
    }

    @Operation(summary = "Confirm a payOS browser-return (verifies via payOS + credits idempotently)")
    @GetMapping("/payos/return")
    public ApiResponse<PaymentResultResponse> payosReturn(@RequestParam Map<String, String> params) {
        return ApiResponse.success("payment.return",
            paymentService.confirmReturn(PaymentProvider.PAYOS, params));
    }
}
