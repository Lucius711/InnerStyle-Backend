package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Unified representation of a Meshy task object returned by the various GET endpoints.
 * Different task types populate different sections:
 * <ul>
 *   <li>image/text/remesh/retexture -&gt; {@code modelUrls} + {@code textureUrls}</li>
 *   <li>rigging/animation -&gt; {@code result}</li>
 * </ul>
 * Unknown properties are ignored for forward-compatibility.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyTaskDto {

    private String id;
    private String type;
    private String status;
    private Integer progress;
    private Map<String, String> modelUrls;
    private List<MeshyTextureDto> textureUrls;
    private String thumbnailUrl;
    private MeshyResultDto result;
    private MeshyTaskErrorDto taskError;
    private Integer consumedCredits;
    private Long createdAt;
    private Long startedAt;
    private Long finishedAt;
}
