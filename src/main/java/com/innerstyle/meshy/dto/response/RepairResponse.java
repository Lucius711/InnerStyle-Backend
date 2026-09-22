package com.innerstyle.meshy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of an in-place watertight repair (or a revert back to the pristine original): the
 * printability report before and after, the updated task (its model now points at the
 * persisted mesh — no file download), and whether a pristine-original backup still exists to
 * revert to.
 */
@Schema(description = "In-place repair/revert result (model fixed/restored and saved)")
public record RepairResponse(
    PrintabilityResponse before,
    PrintabilityResponse after,
    MeshyTaskResponse task,
    boolean hasOriginalBackup) {
}
