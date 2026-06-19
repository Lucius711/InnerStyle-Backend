package com.innerstyle.meshy.client;

import com.innerstyle.meshy.client.dto.MeshyAnimationRequest;
import com.innerstyle.meshy.client.dto.MeshyImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyMultiImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyRemeshRequest;
import com.innerstyle.meshy.client.dto.MeshyRetextureRequest;
import com.innerstyle.meshy.client.dto.MeshyRiggingRequest;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dPreviewRequest;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dRefineRequest;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;

/**
 * Thin abstraction over the MeshyAI REST API. Each {@code create*} method submits a task and
 * returns the Meshy task id; {@link #getTask} retrieves the current task object. Implemented by
 * {@code MeshyRestClient} (real HTTP) and easily mocked in tests.
 */
public interface MeshyClient {

    String createImageTo3d(MeshyImageTo3dRequest request);

    String createMultiImageTo3d(MeshyMultiImageTo3dRequest request);

    String createTextTo3dPreview(MeshyTextTo3dPreviewRequest request);

    String createTextTo3dRefine(MeshyTextTo3dRefineRequest request);

    String createRemesh(MeshyRemeshRequest request);

    String createRetexture(MeshyRetextureRequest request);

    String createRigging(MeshyRiggingRequest request);

    String createAnimation(MeshyAnimationRequest request);

    /** Creative Lab — Figure: stage 1, stylize a source photo into a chibi concept image. */
    String createFigurePrototype(String imageUrl);

    /** Creative Lab — Figure: stage 2, turn a succeeded prototype into a textured 3D figure. */
    String createFigureBuild(String inputTaskId);

    /** Retrieve a task by routing to the endpoint matching {@code type}. */
    MeshyTaskDto getTask(MeshyTaskType type, String meshyTaskId);
}
