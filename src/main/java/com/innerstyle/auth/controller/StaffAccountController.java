package com.innerstyle.auth.controller;

import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.auth.service.AuthService;
import com.innerstyle.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated staff account endpoints ({@code /api/staff/account/**}). Lets a staff-only
 * account load its own profile (the {@code /user/account/**} path requires ROLE_USER).
 */
@Tag(name = "Staff Account")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/staff/account")
@PreAuthorize("hasRole('STAFF')")
@RequiredArgsConstructor
public class StaffAccountController {

    private final AuthService authService;

    @Operation(summary = "Get the current staff member's profile")
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("user.profile", authService.me(principal.getId()));
    }
}
