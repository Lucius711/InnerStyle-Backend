package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for {@code POST /openapi/v1/remesh} (optimization / topology / polycount). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyRemeshRequest {

    private String inputTaskId;
    private String modelUrl;
    private List<String> targetFormats;
    private String topology;
    private Integer targetPolycount;
}
