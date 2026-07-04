package com.innerstyle.meshy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of an in-place watertight repair: the printability report before and after, plus the
 * updated task (its model now points at the repaired, persisted mesh — no file download).
 */
@Schema(description = "In-place repair result (model fixed and saved)")
public record RepairResponse(
    PrintabilityResponse before,
    PrintabilityResponse after,
    MeshyTaskResponse task) {
}
