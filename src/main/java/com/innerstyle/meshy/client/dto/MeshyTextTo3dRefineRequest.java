package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for {@code POST /openapi/v2/text-to-3d} with {@code mode=refine} (adds color/texture). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyTextTo3dRefineRequest {

    private String mode;
    private String previewTaskId;
    private Boolean enablePbr;
    private String texturePrompt;
    private String textureImageUrl;
    private List<String> targetFormats;

    public static MeshyTextTo3dRefineRequest refine(String previewTaskId, Boolean enablePbr,
                                                    String texturePrompt, String textureImageUrl,
                                                    List<String> targetFormats) {
        return new MeshyTextTo3dRefineRequest("refine", previewTaskId, enablePbr,
            texturePrompt, textureImageUrl, targetFormats);
    }
}
