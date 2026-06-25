package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Options for the image-to-3D upload endpoint (the image itself is sent as a multipart file,
 * so there is no {@code imageUrl} here). Bound from multipart form fields via {@code @ModelAttribute}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Options for an image-to-3D task created from an uploaded file")
public class ImageUploadOptions {

    @Schema(description = "Meshy AI model", example = "latest",
            allowableValues = {"meshy-5", "meshy-6", "latest"})
    private String aiModel;

    @Schema(description = "Generate textures/color (default true)", example = "true")
    private Boolean shouldTexture;

    @Schema(description = "Generate PBR maps (metallic/roughness/normal/emission)", example = "true")
    private Boolean enablePbr;

    @Schema(description = "Run the remesh/optimization phase", example = "true")
    private Boolean shouldRemesh;

    @Min(value = 100, message = "1.targetPolycount.range")
    @Max(value = 300000, message = "2.targetPolycount.range")
    @Schema(description = "Target polygon count (100..300000)", example = "30000")
    private Integer targetPolycount;

    @Pattern(regexp = "quad|triangle", message = "1.topology.invalid")
    @Schema(description = "Mesh topology", example = "triangle", allowableValues = {"quad", "triangle"})
    private String topology;

    @Pattern(regexp = "a-pose|t-pose|", message = "1.pose.invalid")
    @Schema(description = "Pose mode for humanoid models", example = "a-pose",
            allowableValues = {"a-pose", "t-pose", ""})
    private String poseMode;

    @Size(max = 600, message = "1.prompt.tooLong")
    @Schema(description = "Optional text prompt to guide texturing/color")
    private String texturePrompt;

    @Schema(description = "Requested output formats", example = "[\"glb\", \"fbx\"]")
    private List<@Pattern(regexp = "glb|obj|fbx|stl|usdz|3mf", message = "1.targetFormats.invalid") String> targetFormats;

    @Schema(description = "Generate the base color texture at 4K (HD). Meshy-6/latest only.", example = "true")
    private Boolean hdTexture;

    @Schema(description = "Optimize/stylize the input image (default true). Set false to preserve the exact face. Meshy-6/latest only.", example = "false")
    private Boolean imageEnhancement;
}
