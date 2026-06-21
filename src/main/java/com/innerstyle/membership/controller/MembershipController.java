package com.innerstyle.membership.controller;

import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.membership.dto.request.SubscribeRequest;
import com.innerstyle.membership.dto.response.MembershipResponse;
import com.innerstyle.membership.service.MembershipService;
import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated membership endpoints ({@code /api/user/membership/**}).
 */
@Tag(name = "Membership")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/user/membership")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;
    private final PaymentService paymentService;

    @Operation(summary = "Get my membership + remaining credits")
    @GetMapping("/me")
    public ApiResponse<MembershipResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("membership.me", membershipService.getMyMembership(principal.getId()));
    }

    @Operation(summary = "Buy / upgrade a plan (starts a VNPay/MoMo payment)")
    @PostMapping("/subscribe")
    public ApiResponse<PaymentInitResponse> subscribe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SubscribeRequest request,
            HttpServletRequest http) {
        PaymentProvider provider = PaymentProvider.valueOf(request.getProvider());
        return ApiResponse.success("membership.subscribe.created",
            paymentService.createSubscriptionPayment(principal.getId(), request.getPlanCode(),
                provider, clientIp(http)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
