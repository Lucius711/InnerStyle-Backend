package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Add a printable base/stand under a model (like Meshy's figure base). Sizes are expressed as
 * ratios of the model's own dimensions so they work regardless of the model's unit scale.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Custom base/stand options")
public class BaseRequest {

    @Pattern(regexp = "cylinder|square|hexagon", message = "1.baseShape.invalid")
    @Schema(description = "Base shape", example = "cylinder",
            allowableValues = {"cylinder", "square", "hexagon"})
    private String shape;

    @DecimalMin(value = "0.01", message = "1.baseHeight.range")
    @DecimalMax(value = "0.5", message = "2.baseHeight.range")
    @Schema(description = "Base thickness as a fraction of the model height (0.01–0.5)",
            example = "0.06")
    private Double heightRatio;

    @DecimalMin(value = "0.0", message = "1.baseMargin.range")
    @DecimalMax(value = "1.0", message = "2.baseMargin.range")
    @Schema(description = "How much wider the base is than the model footprint (0–1)",
            example = "0.1")
    private Double marginRatio;

    @Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "1.baseColor.invalid")
    @Schema(description = "Base color as a hex string (#RRGGBB)", example = "#3a3f55")
    private String color;

    @Size(max = 40, message = "1.signatureText.size")
    @Pattern(regexp = "^[\\p{L}\\p{N} .,'&_-]*$", message = "2.signatureText.invalid")
    @Schema(description = "Optional typed signature engraved on the bottom of the base (used only "
            + "when no hand-drawn strokes are provided). Empty = no text signature.",
            example = "InnerStyle")
    private String signatureText;

    @Size(max = 400, message = "1.signatureStrokes.size")
    @Schema(description = "Hand-drawn signature: a list of strokes, each a list of [x,y] points in "
            + "normalised [0,1] coordinates (y down). Takes priority over signatureText.")
    private List<@Size(max = 4000, message = "1.signatureStroke.size") List<List<Double>>> signatureStrokes;

    @DecimalMin(value = "0.01", message = "1.signaturePenWidth.range")
    @DecimalMax(value = "0.2", message = "2.signaturePenWidth.range")
    @Schema(description = "Pen width for hand-drawn strokes, as a fraction of the drawing box",
            example = "0.04")
    private Double signaturePenWidth;

    @DecimalMin(value = "0.1", message = "1.signatureDepth.range")
    @DecimalMax(value = "0.8", message = "2.signatureDepth.range")
    @Schema(description = "Signature depth as a fraction of base thickness (0.1–0.8)",
            example = "0.35")
    private Double signatureDepthRatio;

    @Schema(description = "Raise the signature instead of engraving it (default false = recessed)",
            example = "false")
    private Boolean signatureRaised;

    public String shapeOrDefault() {
        return (shape == null || shape.isBlank()) ? "cylinder" : shape;
    }

    public double heightRatioOrDefault() {
        return heightRatio == null ? 0.06 : heightRatio;
    }

    public double marginRatioOrDefault() {
        return marginRatio == null ? 0.1 : marginRatio;
    }

    public String colorOrDefault() {
        return (color == null || color.isBlank()) ? "#c8c8c8" : color;
    }

    public String signatureTextOrEmpty() {
        return signatureText == null ? "" : signatureText.trim();
    }

    public double signatureDepthRatioOrDefault() {
        return signatureDepthRatio == null ? 0.35 : signatureDepthRatio;
    }

    public boolean signatureRaisedOrDefault() {
        return signatureRaised != null && signatureRaised;
    }

    public double signaturePenWidthOrDefault() {
        return signaturePenWidth == null ? 0.04 : signaturePenWidth;
    }

    public boolean hasSignatureStrokes() {
        return signatureStrokes != null && !signatureStrokes.isEmpty();
    }
}
