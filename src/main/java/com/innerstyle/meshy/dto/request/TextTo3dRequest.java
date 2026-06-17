package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Create a text-to-3D preview (untextured mesh). Apply color afterward with a refine task.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a text-to-3D preview task")
public class TextTo3dRequest {

    @NotBlank(message = "1.prompt.required")
    @Size(max = 600, message = "2.prompt.tooLong")
    @Schema(description = "What to generate", example = "a futuristic robot warrior")
    private String prompt;

    @Schema(description = "Meshy AI model", example = "latest",
            allowableValues = {"meshy-5", "meshy-6", "latest"})
    private String aiModel;

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

    @Schema(description = "Requested output formats", example = "[\"glb\"]")
    private List<@Pattern(regexp = "glb|obj|fbx|stl|usdz|3mf", message = "1.targetFormats.invalid") String> targetFormats;
}
