package com.innerstyle.meshy.service;

import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.dto.request.AnimateRequest;
import com.innerstyle.meshy.dto.request.BaseRequest;
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
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.ModelOrigin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
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

    /** Creative Lab — Chibi Figurine stage 1 from an uploaded file (+ optional texture description). */
    MeshyTaskResponse createFigurinePrototypeFromUpload(MultipartFile file, String texturePrompt);

    /** Creative Lab — Chibi Figurine stage 2: prototype -> textured 3D figure (build). */
    MeshyTaskResponse buildFigurine(FigurineBuildRequest request);

    /**
     * Import a user-provided 3D file (.glb/.gltf/.obj/.fbx/.stl): store its bytes locally and
     * create a SUCCEEDED task so it appears in the library and can run the rest of the pipeline
     * (custom base, printability, export, and Meshy remesh/retexture/rig via data URI).
     */
    MeshyTaskResponse importModel(MultipartFile file, UUID userId);

    /**
     * Add a printable base/stand under the given task's model and persist it in place (the edited
     * GLB replaces the served model — preview/export/downstream all use it). Owner-only.
     */
    MeshyTaskResponse addBase(UUID taskId, UUID userId, BaseRequest request);

    /**
     * Remove the base previously baked into the given task's model (the geometry named
     * {@code innerstyle_base}) and persist the base-less model in place. Owner-only. Lets the base
     * be changed (remove, then add a new one) or simply taken off without regenerating the model.
     */
    MeshyTaskResponse removeBase(UUID taskId, UUID userId);

    /**
     * Replace the given task's model with the edited mesh exported in-browser (material and
     * transform edits baked via three.js' GLTFExporter). The uploaded GLB is stored in place and
     * becomes the authoritative served model, so preview / export / orders reflect the edits.
     * Owner-only.
     */
    MeshyTaskResponse replaceModel(UUID taskId, UUID userId, byte[] glb);

    /**
     * Repair the given task's model into a watertight, printable mesh and persist it in place
     * (no file download — the served model becomes the repaired one). Owner-only. Returns the
     * before/after printability reports and the updated task.
     */
    com.innerstyle.meshy.dto.response.RepairResponse repairInPlace(UUID taskId, UUID userId);

    /**
     * Repair the given task's model into a watertight, printable mesh and persist it in place,
     * WITHOUT an owner check. For privileged callers only (e.g. STAFF order fulfilment); the caller
     * is responsible for authorization. Returns the before/after printability reports + updated task.
     */
    com.innerstyle.meshy.dto.response.RepairResponse repairInPlace(UUID taskId);

    /**
     * Revert the given task's model back to the pre-repair (or originally uploaded/generated)
     * backup, if one was captured. Owner-only. Returns the before/after printability reports and
     * the updated task; the backup itself is kept, so repair/revert can be repeated freely.
     */
    com.innerstyle.meshy.dto.response.RepairResponse revertToOriginal(UUID taskId, UUID userId);

    /**
     * Revert without an owner check. For privileged callers only (e.g. STAFF order fulfilment);
     * the caller is responsible for authorization.
     */
    com.innerstyle.meshy.dto.response.RepairResponse revertToOriginal(UUID taskId);

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
     * Cheap validator for {@link #fetchModel} (no bytes loaded): changes whenever the served model
     * changes, so the browser can revalidate with If-None-Match and get a 304 instead of re-downloading.
     */
    String modelETag(UUID id, String format);

    /**
     * Cache the browser-built iOS AR Quick Look file (USDZ) for a task so it can be served as a
     * real same-origin URL. iOS Quick Look cannot launch from an in-browser {@code blob:} URL, and
     * locally-edited models have no Meshy-hosted USDZ, so the frontend builds the USDZ (three.js
     * USDZExporter) and uploads the bytes here; they are then served via
     * {@code GET /tasks/{id}/model?format=usdz}.
     *
     * @param id   the task id (must exist)
     * @param data the USDZ file bytes
     */
    void storeUsdz(UUID id, UUID userId, byte[] data);

    /**
     * Store a freshly captured preview image (PNG) for a task and re-point the task's
     * {@code thumbnailUrl} at it. Meshy never regenerates its thumbnail after an in-place edit
     * (e.g. a custom base added via {@code POST /tasks/{id}/base}), so the editor captures the
     * edited model and uploads the PNG here; it is then served via {@code GET /tasks/{id}/thumbnail}.
     * Only the task owner may replace its thumbnail.
     *
     * @param id     the task id (must exist and belong to {@code userId})
     * @param userId the authenticated caller
     * @param data   the PNG image bytes
     */
    void storeThumbnail(UUID id, UUID userId, byte[] data);

    /**
     * Load a task's stored preview image (PNG). Returns {@code null} when the task has no captured
     * thumbnail (e.g. it has never been edited). Public so an {@code <img>} tag can load it.
     */
    byte[] fetchThumbnailImage(UUID id);

    /**
     * Same-origin proxy of one of a task's PBR texture maps (base_color/metallic/normal/...).
     * Meshy sometimes ships GLBs that reference textures externally; those CDN URLs lack CORS so
     * the in-browser viewer can't apply them. The frontend loads the map through this proxy and
     * assigns it to the model's material.
     *
     * @param map which map (defaults to {@code base_color})
     */
    ModelData fetchTexture(UUID id, String map);

    /**
     * Validate an export the given user owns and prepare it for streaming. Runs in the request
     * thread (so ownership / premium / resize errors map to proper HTTP codes before the body is
     * committed). When {@code heightMm} is set, the model is resized here and the resulting bytes
     * are carried in the returned {@link ExportPrep}.
     *
     * @param id        the task id
     * @param userId    the authenticated owner (404 if the task isn't theirs)
     * @param format    requested output format (must be one the task actually produced)
     * @param heightMm  target height in millimetres, or {@code null} to keep the original size.
     *                  Resizing is a premium feature and only supported for printable formats
     *                  (see {@link com.innerstyle.meshy.util.MeshTransformer#supportsResize}).
     * @param origin    where the model's origin sits when resized (defaults to {@code BOTTOM}).
     */
    ExportPrep prepareUserExport(UUID id, UUID userId, String format, Double heightMm,
                                 ModelOrigin origin);

    /** Prepare a task's model for zip export with no ownership check (staff fulfilment). */
    ExportPrep prepareTask(UUID taskId, String format);

    /**
     * Stream a ZIP of the prepared model (plus any PBR texture maps) to {@code out}. The model
     * file is streamed straight from Meshy's CDN into the zip (no full in-memory buffering);
     * textures, which are small, are fetched and added individually. Meant to run inside a
     * {@code StreamingResponseBody}.
     */
    void writeZip(ExportPrep prep, OutputStream out) throws IOException;

    /** Binary model payload: raw bytes + content type + a download filename. */
    record ModelData(byte[] bytes, String contentType, String filename) {
    }

    /** A validated, ready-to-stream export: the task, the resolved format, and (optional) resized bytes. */
    record ExportPrep(MeshyTask task, String fmt, byte[] resizedModel) {
    }

    /**
     * Merge a remote task state (from a webhook callback or the polling fallback) into the
     * stored record. No-op if we don't track the given Meshy task id.
     */
    void applyRemoteState(MeshyTaskDto remote);
}
