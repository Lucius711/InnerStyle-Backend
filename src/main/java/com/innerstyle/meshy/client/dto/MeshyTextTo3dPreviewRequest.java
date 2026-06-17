package com.innerstyle.meshy.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for {@code POST /openapi/v2/text-to-3d} with {@code mode=preview}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeshyTextTo3dPreviewRequest {

    private String mode;
    private String prompt;
    private String aiModel;
    private Boolean shouldRemesh;
    private Integer targetPolycount;
    private String topology;
    private String poseMode;
    private List<String> targetFormats;
    private Boolean moderation;

    public static MeshyTextTo3dPreviewRequest preview(String prompt, String aiModel,
                                                      Boolean shouldRemesh, Integer targetPolycount,
                                                      String topology, String poseMode,
                                                      List<String> targetFormats, Boolean moderation) {
        return new MeshyTextTo3dPreviewRequest("preview", prompt, aiModel, shouldRemesh,
            targetPolycount, topology, poseMode, targetFormats, moderation);
    }
}
