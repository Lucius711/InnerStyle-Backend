package com.innerstyle.meshy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
}
