package com.innerstyle.membership.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** A purchasable plan. */
@Schema(description = "Membership plan")
public record PlanResponse(
    String code,
    String name,
    int monthlyCredits,
    BigDecimal price,
    String currency
) {
}
