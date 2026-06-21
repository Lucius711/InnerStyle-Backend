package com.innerstyle.membership.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Credit cost of a 3D operation (for the client to show before generating). */
@Schema(description = "Operation credit cost")
public record OperationCreditResponse(
    String taskType,
    int creditCost
) {
}
