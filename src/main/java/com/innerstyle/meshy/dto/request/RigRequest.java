package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Auto-rig a humanoid model (adds a skeleton + default walking/running animations),
 * preparing it for the Animation API. Provide a source task id or a GLB model URL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a rigging task (skeleton for animation)")
public class RigRequest {

    @Schema(description = "Our task id of a prior SUCCEEDED textured humanoid task")
    private UUID sourceTaskId;

    @Schema(description = "Public textured humanoid .glb URL if no sourceTaskId")
    private String modelUrl;

    @Positive
    @Schema(description = "Approximate character height in meters", example = "1.7")
    private Double heightMeters;
}
