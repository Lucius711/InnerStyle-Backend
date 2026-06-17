package com.innerstyle.meshy.service;

import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.dto.request.AnimateRequest;
import com.innerstyle.meshy.dto.request.ImageTo3dRequest;
import com.innerstyle.meshy.dto.request.ImageUploadOptions;
import com.innerstyle.meshy.dto.request.RefineRequest;
import com.innerstyle.meshy.dto.request.RemeshRequest;
import com.innerstyle.meshy.dto.request.RetextureRequest;
import com.innerstyle.meshy.dto.request.RigRequest;
import com.innerstyle.meshy.dto.request.TextTo3dRequest;
import com.innerstyle.meshy.dto.response.MeshyTaskResponse;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Orchestrates MeshyAI tasks: submits work, persists tracking records, and serves status.
 */
public interface MeshyTaskService {

    MeshyTaskResponse createImageTo3d(ImageTo3dRequest request);

    /** Image-to-3D from an uploaded file (converted to a base64 data URI for Meshy). */
    MeshyTaskResponse createImageTo3dFromUpload(MultipartFile file, ImageUploadOptions options);

    MeshyTaskResponse createTextTo3dPreview(TextTo3dRequest request);

    MeshyTaskResponse refine(RefineRequest request);

    MeshyTaskResponse remesh(RemeshRequest request);

    MeshyTaskResponse retexture(RetextureRequest request);

    MeshyTaskResponse rig(RigRequest request);

    MeshyTaskResponse animate(AnimateRequest request);

    MeshyTaskResponse getById(UUID id);

    Page<MeshyTaskResponse> list(MeshyTaskStatus status, Pageable pageable);

    /**
     * Merge a remote task state (from a webhook callback or the polling fallback) into the
     * stored record. No-op if we don't track the given Meshy task id.
     */
    void applyRemoteState(MeshyTaskDto remote);
}
