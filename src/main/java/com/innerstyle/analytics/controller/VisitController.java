package com.innerstyle.analytics.controller;

import com.innerstyle.analytics.service.VisitService;
import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.web.ClientIpResolver;
import com.innerstyle.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public visit beacon ({@code /api/common/visits}); the SPA calls it once per browser session. */
@Tag(name = "Visits")
@RestController
@RequestMapping("/common/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @Operation(summary = "Count one site visit for today")
    @PostMapping
    public ApiResponse<Void> record(@AuthenticationPrincipal UserPrincipal principal,
                                    HttpServletRequest request) {
        // Public endpoint: principal is null for guests (or an invalid/expired token).
        visitService.recordVisit(principal == null ? null : principal.getId(),
                ClientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
        return ApiResponse.success("visit.recorded");
    }

    @Operation(summary = "Presence heartbeat; no-op for guests")
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            visitService.heartbeat(principal.getId());
        }
        return ApiResponse.success("visit.heartbeat");
    }
}
