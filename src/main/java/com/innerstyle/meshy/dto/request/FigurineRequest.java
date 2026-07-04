package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Creative Lab — Chibi Figurine, stage 1 (prototype): stylize a source photo into a
 * chibi concept image. Pass a public image URL or a base64 data URI.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a chibi figurine prototype task")
public class FigurineRequest {

    @NotBlank(message = "1.image.required")
    @Schema(description = "Public image URL or base64 data URI (.jpg/.jpeg/.png/.webp)",
            example = "https://example.com/portrait.png")
    private String imageUrl;

    /**
     * Desired texture/color, in words. Meshy's figure stages don't accept a texture prompt, so
     * this is stored on the figurine chain and applied afterwards via a /retexture step.
     */
    @Size(max = 600, message = "1.prompt.tooLong")
    @Schema(description = "Optional texture/color description, applied via retexture after the "
            + "figure is built (e.g. 'soft pastel colors, glossy finish').",
            example = "soft pastel colors, glossy finish")
    private String texturePrompt;
}
