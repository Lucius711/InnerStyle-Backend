package com.innerstyle.meshy.service;

import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.dto.request.AnimateRequest;
import com.innerstyle.meshy.dto.request.FigurineBuildRequest;
import com.innerstyle.meshy.dto.request.FigurineRequest;
import com.innerstyle.meshy.dto.request.ImageTo3dRequest;
import com.innerstyle.meshy.dto.request.ImageUploadOptions;
import com.innerstyle.meshy.dto.request.MultiImageTo3dRequest;
import com.innerstyle.meshy.dto.request.RefineRequest;
import com.innerstyle.meshy.dto.request.RemeshRequest;
import com.innerstyle.meshy.dto.request.RetextureRequest;
import com.innerstyle.meshy.dto.request.RigRequest;
import com.innerstyle.meshy.dto.request.TextTo3dRequest;
import com.innerstyle.meshy.dto.response.MeshyTaskResponse;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.ModelOrigin;
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

    /** Multi-image-to-3D: several reference images of the same subject -> one model. */
    MeshyTaskResponse createMultiImageTo3d(MultiImageTo3dRequest request);

    /** Multi-image-to-3D from uploaded files (converted to base64 data URIs for Meshy). */
    MeshyTaskResponse createMultiImageTo3dFromUpload(java.util.List<MultipartFile> files,
                                                     ImageUploadOptions options);

    MeshyTaskResponse createTextTo3dPreview(TextTo3dRequest request);

    MeshyTaskResponse refine(RefineRequest request);

    MeshyTaskResponse remesh(RemeshRequest request);

    MeshyTaskResponse retexture(RetextureRequest request);

    MeshyTaskResponse rig(RigRequest request);

    MeshyTaskResponse animate(AnimateRequest request);

    /** Creative Lab — Chibi Figurine stage 1: photo -> chibi concept image (prototype). */
    MeshyTaskResponse createFigurinePrototype(FigurineRequest request);

    /** Creative Lab — Chibi Figurine stage 1 from an uploaded file. */
    MeshyTaskResponse createFigurinePrototypeFromUpload(MultipartFile file);

    /** Creative Lab — Chibi Figurine stage 2: prototype -> textured 3D figure (build). */
    MeshyTaskResponse buildFigurine(FigurineBuildRequest request);

    /** Fetch a task the given user owns (404 if it isn't theirs). */
    MeshyTaskResponse getById(UUID id, UUID userId);

    /** List the given user's own tasks (private library), optionally filtered by status. */
    Page<MeshyTaskResponse> list(UUID userId, MeshyTaskStatus status, Pageable pageable);

    /** Permanently delete a task the given user owns (404 if it isn't theirs). */
    void delete(UUID id, UUID userId);

    /**
     * Server-side proxy of a task's model/animation file. Meshy result URLs live on a CDN that
     * does not send CORS headers, so the browser cannot load them into a 3D viewer directly.
     * The backend fetches the bytes (no CORS restriction server-to-server) and returns them so
     * the frontend can stream the model same-origin.
     *
     * @param format requested format (e.g. {@code glb}); falls back to glb/gltf, then any available.
     */
    ModelData fetchModel(UUID id, String format);

    /**
     * Export a model the given user owns, optionally resized to a physical height and with its
     * origin repositioned (for download / 3D printing).
     *
     * @param id        the task id
     * @param userId    the authenticated owner (404 if the task isn't theirs)
     * @param format    requested output format (must be one the task actually produced)
     * @param heightMm  target height in millimetres, or {@code null} to keep the original size.
     *                  Resizing is a premium feature and only supported for printable formats
     *                  (see {@link com.innerstyle.meshy.util.MeshTransformer#supportsResize}).
     * @param origin    where the model's origin sits when resized (defaults to {@code BOTTOM}).
     */
    ModelData exportModel(UUID id, UUID userId, String format, Double heightMm, ModelOrigin origin);

    /** Binary model payload: raw bytes + content type + a download filename. */
    record ModelData(byte[] bytes, String contentType, String filename) {
    }

    /**
     * Merge a remote task state (from a webhook callback or the polling fallback) into the
     * stored record. No-op if we don't track the given Meshy task id.
     */
    void applyRemoteState(MeshyTaskDto remote);
}
