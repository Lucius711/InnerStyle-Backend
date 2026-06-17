package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Re-color / re-texture an existing model via a text style prompt or a style image.
 * Provide either a source task id or a model URL, and at least one style input.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a retexture (re-color) task")
public class RetextureRequest {

    @Schema(description = "Our task id of a prior SUCCEEDED task to re-color")
    private UUID sourceTaskId;

    @Schema(description = "Public model URL if no sourceTaskId")
    private String modelUrl;

    @Size(max = 600, message = "1.prompt.tooLong")
    @Schema(description = "Text describing the desired color/texture style",
            example = "red fangs, samurai outfit with batik patterns")
    private String textStylePrompt;

    @Schema(description = "Style image URL to guide color/texture")
    private String imageStyleUrl;

    @Schema(description = "Meshy AI model", example = "latest")
    private String aiModel;

    @Schema(description = "Generate PBR maps", example = "true")
    private Boolean enablePbr;

    @Schema(description = "Reuse the model's original UVs", example = "true")
    private Boolean enableOriginalUv;

    @Schema(description = "Requested output formats", example = "[\"glb\"]")
    private List<@Pattern(regexp = "glb|obj|fbx|stl|usdz|3mf", message = "1.targetFormats.invalid") String> targetFormats;
}
