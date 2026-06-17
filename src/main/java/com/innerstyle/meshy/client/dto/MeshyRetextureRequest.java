package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for {@code POST /openapi/v1/retexture} (re-color via text or image style). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyRetextureRequest {

    private String inputTaskId;
    private String modelUrl;
    private String textStylePrompt;
    private String imageStyleUrl;
    private String aiModel;
    private Boolean enablePbr;
    private Boolean enableOriginalUv;
    private List<String> targetFormats;
}
