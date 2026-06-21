package com.innerstyle.membership.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** The current user's membership + remaining credits. */
@Schema(description = "Current membership")
public record MembershipResponse(
    String planCode,
    String planName,
    int creditsRemaining,
    int monthlyCredits,
    Instant periodEnd,
    String status
) {
}
