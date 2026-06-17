package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Default walking/running animations produced by the rigging task.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyBasicAnimationsDto {

    private String walkingGlbUrl;
    private String walkingFbxUrl;
    private String walkingArmatureGlbUrl;
    private String runningGlbUrl;
    private String runningFbxUrl;
    private String runningArmatureGlbUrl;
}
