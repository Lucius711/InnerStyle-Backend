package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Creative Lab — Chibi Figurine, stage 2 (build): turn a SUCCEEDED figurine prototype task
 * into a textured 3D figure.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a chibi figurine build task")
public class FigurineBuildRequest {

    @NotNull(message = "1.sourceTaskId.required")
    @Schema(description = "Our task id of a SUCCEEDED figurine prototype task")
    private UUID sourceTaskId;

    /**
     * Optional texture/color description. If omitted, the value entered at stage 1 is inherited.
     * Stored on the build task so it can be applied via /retexture once the figure is built.
     */
    @Size(max = 600, message = "1.prompt.tooLong")
    @Schema(description = "Optional texture/color description (overrides the stage-1 value), "
            + "applied via retexture after the build.")
    private String texturePrompt;
}
