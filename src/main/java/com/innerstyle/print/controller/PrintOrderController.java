package com.innerstyle.print.controller;

import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.print.dto.request.CreatePrintOrderRequest;
import com.innerstyle.print.dto.response.PrintOrderInitResponse;
import com.innerstyle.print.dto.response.PrintOrderResponse;
import com.innerstyle.print.service.PrintOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authenticated 3D-print order endpoints ({@code /api/user/print/**}). Placing an order starts a
 * direct payOS payment and returns the gateway URL.
 */
@Tag(name = "3D Print")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/user/print")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class PrintOrderController {

    private final PrintOrderService printOrderService;

    @Operation(summary = "Place a 3D-print order and start payment (payOS)")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PrintOrderInitResponse> placeOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePrintOrderRequest request,
            HttpServletRequest http) {
        return ApiResponse.success("print.order.placed",
                printOrderService.placeOrder(principal.getId(), request, clientIp(http)));
    }

    @Operation(summary = "List my 3D-print orders")
    @GetMapping("/orders")
    public ApiResponse<Page<PrintOrderResponse>> myOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success("print.orders", printOrderService.list(principal.getId(), pageable));
    }

    @Operation(summary = "Continue paying one of my PENDING orders (reuses its payOS link)")
    @PostMapping("/orders/{id}/pay")
    public ApiResponse<PrintOrderInitResponse> resumePayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            HttpServletRequest http) {
        return ApiResponse.success("print.order.payment",
                printOrderService.resumePayment(principal.getId(), id, clientIp(http)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
