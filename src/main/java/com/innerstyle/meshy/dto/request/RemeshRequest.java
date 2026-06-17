package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Optimize an existing model (topology / polycount / formats). Provide either a source task
 * id (one of our prior SUCCEEDED tasks) or a model URL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a remesh/optimization task")
public class RemeshRequest {

    @Schema(description = "Our task id of a prior SUCCEEDED task to optimize")
    private UUID sourceTaskId;

    @Schema(description = "Public model URL (.glb/.gltf/.obj/.fbx/.stl) if no sourceTaskId")
    private String modelUrl;

    @Pattern(regexp = "quad|triangle", message = "1.topology.invalid")
    @Schema(description = "Mesh topology", example = "quad", allowableValues = {"quad", "triangle"})
    private String topology;

    @Min(value = 100, message = "1.targetPolycount.range")
    @Max(value = 300000, message = "2.targetPolycount.range")
    @Schema(description = "Target polygon count (100..300000)", example = "50000")
    private Integer targetPolycount;

    @Schema(description = "Requested output formats", example = "[\"glb\", \"fbx\"]")
    private List<@Pattern(regexp = "glb|obj|fbx|stl|usdz|3mf|blend", message = "1.targetFormats.invalid") String> targetFormats;
}
