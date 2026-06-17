package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The {@code result} object returned by rigging and animation tasks. Fields are populated
 * depending on the task type (rigging vs animate); unused fields are {@code null}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyResultDto {

    // Rigging output
    private String riggedCharacterFbxUrl;
    private String riggedCharacterGlbUrl;
    private MeshyBasicAnimationsDto basicAnimations;

    // Animation output
    private String animationGlbUrl;
    private String animationFbxUrl;
    private String processedUsdzUrl;
    private String processedArmatureFbxUrl;
    private String processedAnimationFpsFbxUrl;
}
