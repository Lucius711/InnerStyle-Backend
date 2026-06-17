package com.innerstyle.meshy.dto.response;

import com.innerstyle.meshy.client.dto.MeshyTextureDto;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API representation of a Meshy task and its current results.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A 3D generation task and its results")
public class MeshyTaskResponse {

    @Schema(description = "Our internal task id")
    private UUID id;

    @Schema(description = "MeshyAI task id")
    private String meshyTaskId;

    private MeshyTaskType taskType;

    @Schema(description = "PENDING | IN_PROGRESS | SUCCEEDED | FAILED | CANCELED")
    private MeshyTaskStatus status;

    @Schema(description = "Progress 0..100", example = "100")
    private int progress;

    @Schema(description = "Id of the preceding task, if any")
    private UUID parentId;

    private String prompt;

    @Schema(description = "Model files keyed by format (glb, fbx, obj, usdz, stl, ...)")
    private Map<String, String> modelUrls;

    @Schema(description = "PBR texture/color maps")
    private List<MeshyTextureDto> textureUrls;

    @Schema(description = "Rigging/animation output URLs keyed by name")
    private Map<String, String> animationUrls;

    private String thumbnailUrl;

    @Schema(description = "Error message when status = FAILED")
    private String errorMessage;

    private Integer consumedCredits;

    private Instant createdAt;

    private Instant updatedAt;
}
