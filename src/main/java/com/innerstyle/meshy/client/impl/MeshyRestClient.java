package com.innerstyle.meshy.client.impl;

import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.meshy.client.MeshyClient;
import com.innerstyle.meshy.client.dto.MeshyAnimationRequest;
import com.innerstyle.meshy.client.dto.MeshyCreateResponse;
import com.innerstyle.meshy.client.dto.MeshyImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyMultiImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyRemeshRequest;
import com.innerstyle.meshy.client.dto.MeshyRetextureRequest;
import com.innerstyle.meshy.client.dto.MeshyRiggingRequest;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dPreviewRequest;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dRefineRequest;
import com.innerstyle.meshy.config.MeshyClientConfig;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real HTTP implementation of {@link MeshyClient} backed by Spring {@link RestClient}.
 * Upstream failures are wrapped in {@link UpstreamServiceException} (mapped to 502).
 */
@Slf4j
@Component
public class MeshyRestClient implements MeshyClient {

    private final RestClient restClient;

    public MeshyRestClient(@Qualifier(MeshyClientConfig.MESHY_REST_CLIENT) RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String createImageTo3d(MeshyImageTo3dRequest request) {
        return create(MeshyTaskType.IMAGE_TO_3D.collectionPath(), request);
    }

    @Override
    public String createMultiImageTo3d(MeshyMultiImageTo3dRequest request) {
        return create(MeshyTaskType.MULTI_IMAGE_TO_3D.collectionPath(), request);
    }

    @Override
    public String createTextTo3dPreview(MeshyTextTo3dPreviewRequest request) {
        return create(MeshyTaskType.TEXT_TO_3D_PREVIEW.collectionPath(), request);
    }

    @Override
    public String createTextTo3dRefine(MeshyTextTo3dRefineRequest request) {
        return create(MeshyTaskType.TEXT_TO_3D_REFINE.collectionPath(), request);
    }

    @Override
    public String createRemesh(MeshyRemeshRequest request) {
        return create(MeshyTaskType.REMESH.collectionPath(), request);
    }

    @Override
    public String createRetexture(MeshyRetextureRequest request) {
        return create(MeshyTaskType.RETEXTURE.collectionPath(), request);
    }

    @Override
    public String createRigging(MeshyRiggingRequest request) {
        return create(MeshyTaskType.RIG.collectionPath(), request);
    }

    @Override
    public String createAnimation(MeshyAnimationRequest request) {
        return create(MeshyTaskType.ANIMATE.collectionPath(), request);
    }

    @Override
    public String createFigurePrototype(String imageUrl) {
        return create(MeshyTaskType.FIGURE_PROTOTYPE.collectionPath(),
            java.util.Map.of("image_url", imageUrl));
    }

    @Override
    public String createFigureBuild(String inputTaskId) {
        return create(MeshyTaskType.FIGURE_BUILD.collectionPath(),
            java.util.Map.of("input_task_id", inputTaskId));
    }

    @Override
    public MeshyTaskDto getTask(MeshyTaskType type, String meshyTaskId) {
        try {
            return restClient.get()
                .uri(type.resourcePath(meshyTaskId))
                .retrieve()
                .body(MeshyTaskDto.class);
        } catch (RestClientResponseException ex) {
            log.error("Meshy GET {} failed: {} {}", type, ex.getStatusCode(),
                ex.getResponseBodyAsString());
            throw new UpstreamServiceException("meshy.upstreamError");
        } catch (RuntimeException ex) {
            log.error("Meshy GET {} error", type, ex);
            throw new UpstreamServiceException("meshy.upstreamError");
        }
    }

    private String create(String path, Object body) {
        try {
            MeshyCreateResponse response = restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(MeshyCreateResponse.class);
            if (response == null || response.getResult() == null || response.getResult().isBlank()) {
                throw new UpstreamServiceException("meshy.upstreamError");
            }
            return response.getResult();
        } catch (RestClientResponseException ex) {
            log.error("Meshy POST {} failed: {} {}", path, ex.getStatusCode(),
                ex.getResponseBodyAsString());
            throw new UpstreamServiceException("meshy.upstreamError");
        } catch (RuntimeException ex) {
            log.error("Meshy POST {} error", path, ex);
            throw new UpstreamServiceException("meshy.upstreamError");
        }
    }
}
