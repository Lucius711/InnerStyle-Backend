package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for {@code POST /openapi/v1/multi-image-to-3d}. Null fields are omitted. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyMultiImageTo3dRequest {

    private List<String> imageUrls;
    private String aiModel;
    private Boolean shouldTexture;
    private Boolean enablePbr;
    private Boolean shouldRemesh;
    private Integer targetPolycount;
    private String topology;
    private String texturePrompt;
    private List<String> targetFormats;
}
