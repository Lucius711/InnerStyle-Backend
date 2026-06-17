package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Apply an animation action to a previously rigged character. Optional FPS post-processing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create an animation task for a rigged character")
public class AnimateRequest {

    @NotNull(message = "1.sourceTaskId.required")
    @Schema(description = "Our task id of a SUCCEEDED rigging task")
    private UUID rigTaskId;

    @NotNull(message = "1.actionId.required")
    @Schema(description = "Animation action id from the Meshy animation library", example = "92")
    private Integer actionId;

    @Pattern(regexp = "change_fps|fbx2usdz|extract_armature", message = "1.operationType.invalid")
    @Schema(description = "Optional post-processing operation",
            allowableValues = {"change_fps", "fbx2usdz", "extract_armature"})
    private String operationType;

    @Pattern(regexp = "24|25|30|60", message = "1.fps.invalid")
    @Schema(description = "Target FPS when operationType=change_fps", example = "30",
            allowableValues = {"24", "25", "30", "60"})
    private String fps;
}
