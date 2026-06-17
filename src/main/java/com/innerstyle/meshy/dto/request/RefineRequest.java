package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Apply color/texture to a completed text-to-3D preview task (refine stage).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a text-to-3D refine (texturing/color) task")
public class RefineRequest {

    @NotNull(message = "1.sourceTaskId.required")
    @Schema(description = "Our task id of a SUCCEEDED text-to-3D preview task")
    private UUID sourceTaskId;

    @Schema(description = "Generate PBR maps (metallic/roughness/normal/emission)", example = "true")
    private Boolean enablePbr;

    @Size(max = 600, message = "1.prompt.tooLong")
    @Schema(description = "Optional text prompt to guide color/texture")
    private String texturePrompt;

    @Schema(description = "Optional 2D image URL to guide color/texture")
    private String textureImageUrl;

    @Schema(description = "Requested output formats", example = "[\"glb\"]")
    private List<@Pattern(regexp = "glb|obj|fbx|stl|usdz|3mf", message = "1.targetFormats.invalid") String> targetFormats;
}
