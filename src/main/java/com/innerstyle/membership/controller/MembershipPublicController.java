package com.innerstyle.membership.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.membership.dto.response.OperationCreditResponse;
import com.innerstyle.membership.dto.response.PlanResponse;
import com.innerstyle.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public membership info: the plans and the credit cost of each 3D operation.
 */
@Tag(name = "Membership (public)")
@RestController
@RequestMapping("/common/membership")
@RequiredArgsConstructor
public class MembershipPublicController {

    private final MembershipService membershipService;

    @Operation(summary = "List membership plans")
    @GetMapping("/plans")
    public ApiResponse<List<PlanResponse>> plans() {
        return ApiResponse.success("membership.plans", membershipService.listPlans());
    }

    @Operation(summary = "List credit cost per 3D operation")
    @GetMapping("/operation-credits")
    public ApiResponse<List<OperationCreditResponse>> operationCredits() {
        return ApiResponse.success("membership.operationCredits",
            membershipService.listOperationCredits());
    }
}
