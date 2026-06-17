package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for {@code POST /openapi/v1/rigging} (adds a skeleton for animation). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyRiggingRequest {

    private String inputTaskId;
    private String modelUrl;
    private Double heightMeters;
    private String textureImageUrl;
}
