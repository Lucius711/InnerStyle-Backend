package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Convert multiple reference images (different views of the same subject) into a single 3D model.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a multi-image-to-3D task")
public class MultiImageTo3dRequest {

    @NotEmpty(message = "1.image.required")
    @Size(min = 1, max = 4, message = "1.images.count")
    @Schema(description = "1-4 public image URLs or base64 data URIs of the same subject",
            example = "[\"https://example.com/front.png\", \"https://example.com/side.png\"]")
    private List<String> imageUrls;

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

    @Size(max = 600, message = "1.prompt.tooLong")
    @Schema(description = "Optional text prompt to guide texturing/color")
    private String texturePrompt;

    @Schema(description = "Requested output formats", example = "[\"glb\"]")
    private List<@Pattern(regexp = "glb|obj|fbx|stl|usdz|3mf", message = "1.targetFormats.invalid") String> targetFormats;
}
