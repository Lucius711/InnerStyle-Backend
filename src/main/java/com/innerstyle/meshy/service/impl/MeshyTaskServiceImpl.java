package com.innerstyle.meshy.service.impl;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.meshy.client.MeshyClient;
import com.innerstyle.meshy.client.dto.MeshyAnimationRequest;
import com.innerstyle.meshy.client.dto.MeshyImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyMultiImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyRemeshRequest;
import com.innerstyle.meshy.client.dto.MeshyResultDto;
import com.innerstyle.meshy.client.dto.MeshyRetextureRequest;
import com.innerstyle.meshy.client.dto.MeshyRiggingRequest;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dPreviewRequest;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dRefineRequest;
import com.innerstyle.meshy.config.MeshyProperties;
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
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.mapper.MeshyTaskMapper;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.service.MeshyTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeshyTaskServiceImpl implements MeshyTaskService {

    private final MeshyClient meshyClient;
    private final MeshyTaskRepository taskRepository;
    private final MeshyTaskMapper taskMapper;
    private final MeshyProperties properties;

    private static final Set<String> ALLOWED_IMAGE_TYPES =
        Set.of("image/jpeg", "image/jpg", "image/png");

    /** Shared client for streaming model/animation files from the (CORS-less) Meshy CDN. */
    private static final HttpClient MODEL_HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Override
    @Transactional
    public MeshyTaskResponse createImageTo3d(ImageTo3dRequest request) {
        return submitImageTo3d(request.getImageUrl(), shorten(request.getImageUrl()),
            request.getAiModel(), request.getShouldTexture(), request.getEnablePbr(),
            request.getShouldRemesh(), request.getTargetPolycount(), request.getTopology(),
            request.getPoseMode(), request.getTexturePrompt(), request.getTextureImageUrl(),
            request.getTargetFormats());
    }

    @Override
    @Transactional
    public MeshyTaskResponse createImageTo3dFromUpload(MultipartFile file, ImageUploadOptions options) {
        String dataUri = toDataUri(file);
        String sourceLabel = "upload:" + (file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "image");
        return submitImageTo3d(dataUri, sourceLabel,
            options.getAiModel(), options.getShouldTexture(), options.getEnablePbr(),
            options.getShouldRemesh(), options.getTargetPolycount(), options.getTopology(),
            options.getPoseMode(), options.getTexturePrompt(), null,
            options.getTargetFormats());
    }

    @Override
    @Transactional
    public MeshyTaskResponse createFigurinePrototype(FigurineRequest request) {
        ensureConfigured();
        String meshyTaskId = meshyClient.createFigurePrototype(request.getImageUrl());
        MeshyTask task = newTask(MeshyTaskType.FIGURE_PROTOTYPE, meshyTaskId, null, null);
        task.setSourceImageUrl(shorten(request.getImageUrl()));
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse createFigurinePrototypeFromUpload(MultipartFile file) {
        ensureConfigured();
        String dataUri = toDataUri(file);
        String meshyTaskId = meshyClient.createFigurePrototype(dataUri);
        MeshyTask task = newTask(MeshyTaskType.FIGURE_PROTOTYPE, meshyTaskId, null, null);
        String label = "upload:" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "image");
        task.setSourceImageUrl(label);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse buildFigurine(FigurineBuildRequest request) {
        ensureConfigured();
        MeshyTask proto = requireSucceeded(request.getSourceTaskId(), MeshyTaskType.FIGURE_PROTOTYPE);
        String meshyTaskId = meshyClient.createFigureBuild(proto.getMeshyTaskId());
        MeshyTask task = newTask(MeshyTaskType.FIGURE_BUILD, meshyTaskId, null, proto.getId());
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse createMultiImageTo3d(MultiImageTo3dRequest request) {
        ensureConfigured();
        var meshyRequest = new MeshyMultiImageTo3dRequest(
            request.getImageUrls(), request.getAiModel(), request.getShouldTexture(),
            request.getEnablePbr(), request.getShouldRemesh(), request.getTargetPolycount(),
            request.getTopology(), request.getTexturePrompt(), request.getTargetFormats());

        String meshyTaskId = meshyClient.createMultiImageTo3d(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.MULTI_IMAGE_TO_3D, meshyTaskId,
            request.getTexturePrompt(), null);
        int count = request.getImageUrls() != null ? request.getImageUrls().size() : 0;
        task.setSourceImageUrl("multi-image:" + count);
        return persistAndMap(task);
    }

    private MeshyTaskResponse submitImageTo3d(String imageUrl, String sourceLabel, String aiModel,
                                              Boolean shouldTexture, Boolean enablePbr, Boolean shouldRemesh,
                                              Integer targetPolycount, String topology, String poseMode,
                                              String texturePrompt, String textureImageUrl,
                                              List<String> targetFormats) {
        ensureConfigured();
        var meshyRequest = new MeshyImageTo3dRequest(
            imageUrl, aiModel, shouldTexture, enablePbr, shouldRemesh, targetPolycount,
            topology, poseMode, texturePrompt, textureImageUrl, targetFormats, null);

        String meshyTaskId = meshyClient.createImageTo3d(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.IMAGE_TO_3D, meshyTaskId, texturePrompt, null);
        task.setSourceImageUrl(sourceLabel);
        return persistAndMap(task);
    }

    /** Convert an uploaded image into a {@code data:<mime>;base64,<...>} URI accepted by Meshy. */
    private String toDataUri(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("validation.image.required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("validation.image.unsupportedType");
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + contentType + ";base64," + base64;
        } catch (IOException ex) {
            log.error("Failed to read uploaded image: {}", ex.getMessage(), ex);
            throw new BadRequestException("validation.image.readFailed");
        }
    }

    @Override
    @Transactional
    public MeshyTaskResponse createTextTo3dPreview(TextTo3dRequest request) {
        ensureConfigured();
        var meshyRequest = MeshyTextTo3dPreviewRequest.preview(
            request.getPrompt(), request.getAiModel(), request.getShouldRemesh(),
            request.getTargetPolycount(), request.getTopology(), request.getPoseMode(),
            request.getTargetFormats(), null);

        String meshyTaskId = meshyClient.createTextTo3dPreview(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.TEXT_TO_3D_PREVIEW, meshyTaskId, request.getPrompt(), null);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse refine(RefineRequest request) {
        ensureConfigured();
        MeshyTask preview = requireSucceeded(request.getSourceTaskId(), MeshyTaskType.TEXT_TO_3D_PREVIEW);

        var meshyRequest = MeshyTextTo3dRefineRequest.refine(
            preview.getMeshyTaskId(), request.getEnablePbr(), request.getTexturePrompt(),
            request.getTextureImageUrl(), request.getTargetFormats());

        String meshyTaskId = meshyClient.createTextTo3dRefine(meshyRequest);

        String prompt = request.getTexturePrompt() != null ? request.getTexturePrompt() : preview.getPrompt();
        MeshyTask task = newTask(MeshyTaskType.TEXT_TO_3D_REFINE, meshyTaskId, prompt, preview.getId());
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse remesh(RemeshRequest request) {
        ensureConfigured();
        SourceRef source = resolveSource(request.getSourceTaskId(), request.getModelUrl(), null);

        var meshyRequest = new MeshyRemeshRequest(
            source.inputTaskId(), source.modelUrl(), request.getTargetFormats(),
            request.getTopology(), request.getTargetPolycount());

        String meshyTaskId = meshyClient.createRemesh(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.REMESH, meshyTaskId, null, source.parentId());
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse retexture(RetextureRequest request) {
        ensureConfigured();
        if (isBlank(request.getTextStylePrompt()) && isBlank(request.getImageStyleUrl())) {
            throw new BadRequestException("validation.style.required");
        }
        SourceRef source = resolveSource(request.getSourceTaskId(), request.getModelUrl(), null);

        var meshyRequest = new MeshyRetextureRequest(
            source.inputTaskId(), source.modelUrl(), request.getTextStylePrompt(),
            request.getImageStyleUrl(), request.getAiModel(), request.getEnablePbr(),
            request.getEnableOriginalUv(), request.getTargetFormats());

        String meshyTaskId = meshyClient.createRetexture(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.RETEXTURE, meshyTaskId, request.getTextStylePrompt(),
            source.parentId());
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse rig(RigRequest request) {
        ensureConfigured();
        SourceRef source = resolveSource(request.getSourceTaskId(), request.getModelUrl(), null);

        var meshyRequest = new MeshyRiggingRequest(
            source.inputTaskId(), source.modelUrl(), request.getHeightMeters(), null);

        String meshyTaskId = meshyClient.createRigging(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.RIG, meshyTaskId, null, source.parentId());
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse animate(AnimateRequest request) {
        ensureConfigured();
        MeshyTask rig = requireSucceeded(request.getRigTaskId(), MeshyTaskType.RIG);

        MeshyAnimationRequest.PostProcess postProcess = null;
        if (!isBlank(request.getOperationType())) {
            Integer fps = "change_fps".equals(request.getOperationType()) && request.getFps() != null
                ? Integer.valueOf(request.getFps())
                : null;
            postProcess = new MeshyAnimationRequest.PostProcess(request.getOperationType(), fps);
        }

        var meshyRequest = new MeshyAnimationRequest(rig.getMeshyTaskId(), request.getActionId(), postProcess);

        String meshyTaskId = meshyClient.createAnimation(meshyRequest);

        MeshyTask task = newTask(MeshyTaskType.ANIMATE, meshyTaskId, null, rig.getId());
        return persistAndMap(task);
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskResponse getById(UUID id) {
        return taskMapper.toResponse(getTaskOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeshyTaskResponse> list(MeshyTaskStatus status, Pageable pageable) {
        Page<MeshyTask> page = (status == null)
            ? taskRepository.findAll(pageable)
            : taskRepository.findByStatus(status, pageable);
        return page.map(taskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskService.ModelData fetchModel(UUID id, String format) {
        MeshyTask task = getTaskOrThrow(id);
        String fmt = (format == null || format.isBlank()) ? null : format.toLowerCase();
        String url = resolveAssetUrl(task, fmt);
        if (url == null) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        try {
            HttpResponse<byte[]> resp = MODEL_HTTP.send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Model fetch failed ({}) for task {}", resp.statusCode(), id);
                throw new UpstreamServiceException("meshy.upstreamError");
            }
            String contentType = resp.headers().firstValue("content-type")
                .orElseGet(() -> contentTypeFor(fmt));
            return new MeshyTaskService.ModelData(resp.body(), contentType,
                "model." + (fmt != null ? fmt : "glb"));
        } catch (IOException e) {
            log.warn("Model fetch error for task {}: {}", id, e.getMessage());
            throw new UpstreamServiceException("meshy.upstreamError");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("meshy.upstreamError");
        }
    }

    /**
     * Pick the best asset URL. Prefers an explicitly requested format, then a web-friendly
     * .glb/.gltf among ALL outputs (so the in-browser viewer can play animations from
     * animate-task results), then any model, then any animation.
     */
    private String resolveAssetUrl(MeshyTask task, String fmt) {
        Map<String, String> models = task.getModelUrls();
        Map<String, String> anims = task.getAnimationUrls();
        if (fmt != null) {
            if (models != null && models.get(fmt) != null) {
                return models.get(fmt);
            }
            if (anims != null && anims.get(fmt) != null) {
                return anims.get(fmt);
            }
        }
        // Prefer a web-renderable file (.glb then .gltf) from either map.
        for (String ext : List.of(".glb", ".gltf")) {
            String byExt = firstUrlEndingWith(models, ext);
            if (byExt != null) {
                return byExt;
            }
            byExt = firstUrlEndingWith(anims, ext);
            if (byExt != null) {
                return byExt;
            }
        }
        if (models != null && !models.isEmpty()) {
            return models.values().iterator().next();
        }
        if (anims != null && !anims.isEmpty()) {
            return anims.values().iterator().next();
        }
        return null;
    }

    /** First URL whose path (ignoring query string) ends with the given extension. */
    private String firstUrlEndingWith(Map<String, String> urls, String ext) {
        if (urls == null) {
            return null;
        }
        for (String url : urls.values()) {
            if (url == null) {
                continue;
            }
            int q = url.indexOf('?');
            String path = q >= 0 ? url.substring(0, q) : url;
            if (path.toLowerCase().endsWith(ext)) {
                return url;
            }
        }
        return null;
    }

    private String contentTypeFor(String fmt) {
        if (fmt == null) {
            return "application/octet-stream";
        }
        return switch (fmt) {
            case "glb" -> "model/gltf-binary";
            case "gltf" -> "model/gltf+json";
            case "usdz" -> "model/vnd.usdz+zip";
            default -> "application/octet-stream";
        };
    }

    @Override
    @Transactional
    public void applyRemoteState(MeshyTaskDto remote) {
        if (remote == null || remote.getId() == null) {
            return;
        }
        Optional<MeshyTask> found = taskRepository.findByMeshyTaskId(remote.getId());
        if (found.isEmpty()) {
            log.debug("Ignoring update for untracked Meshy task {}", remote.getId());
            return;
        }
        MeshyTask task = found.get();
        mergeInto(task, remote);
        taskRepository.save(task);
        log.info("Synced Meshy task {} -> {} ({}%)", task.getMeshyTaskId(), task.getStatus(),
            task.getProgress());
    }

    // ------------------------------------------------------------------ helpers

    private void ensureConfigured() {
        if (!properties.hasApiKey()) {
            throw new UpstreamServiceException("meshy.apiKeyMissing");
        }
    }

    private MeshyTask newTask(MeshyTaskType type, String meshyTaskId, String prompt, UUID parentId) {
        MeshyTask task = new MeshyTask();
        task.setTaskType(type);
        task.setMeshyTaskId(meshyTaskId);
        task.setStatus(MeshyTaskStatus.PENDING);
        task.setProgress(0);
        task.setPrompt(prompt);
        task.setParentId(parentId);
        return task;
    }

    private MeshyTaskResponse persistAndMap(MeshyTask task) {
        return taskMapper.toResponse(taskRepository.save(task));
    }

    private MeshyTask getTaskOrThrow(UUID id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("meshy.task.notFound"));
    }

    /** Resolve a SUCCEEDED prior task of a specific type and return its entity. */
    private MeshyTask requireSucceeded(UUID id, MeshyTaskType expectedType) {
        MeshyTask task = getTaskOrThrow(id);
        if (expectedType != null && task.getTaskType() != expectedType) {
            throw new BadRequestException("meshy.task.notFound");
        }
        if (task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            throw new BadRequestException("meshy.task.notSucceeded");
        }
        return task;
    }

    /**
     * Resolve a source: either our prior task id (-> Meshy input_task_id + parent link) or a model URL.
     * Exactly one must be provided.
     */
    private SourceRef resolveSource(UUID sourceTaskId, String modelUrl, MeshyTaskType expectedType) {
        boolean hasTask = sourceTaskId != null;
        boolean hasUrl = !isBlank(modelUrl);
        if (hasTask == hasUrl) {
            throw new BadRequestException("validation.sourceTaskId.required");
        }
        if (hasTask) {
            MeshyTask source = requireSucceeded(sourceTaskId, expectedType);
            return new SourceRef(source.getMeshyTaskId(), null, source.getId());
        }
        return new SourceRef(null, modelUrl, null);
    }

    private void mergeInto(MeshyTask task, MeshyTaskDto remote) {
        if (remote.getStatus() != null) {
            task.setStatus(parseStatus(remote.getStatus()));
        }
        if (remote.getProgress() != null) {
            task.setProgress(remote.getProgress());
        }
        if (remote.getModelUrls() != null && !remote.getModelUrls().isEmpty()) {
            task.setModelUrls(remote.getModelUrls());
        }
        if (remote.getTextureUrls() != null && !remote.getTextureUrls().isEmpty()) {
            task.setTextureUrls(remote.getTextureUrls());
        }
        if (remote.getThumbnailUrl() != null) {
            task.setThumbnailUrl(remote.getThumbnailUrl());
        }
        if (remote.getConsumedCredits() != null) {
            task.setConsumedCredits(remote.getConsumedCredits());
        }
        Map<String, String> animations = buildAnimationUrls(remote.getResult());
        if (!animations.isEmpty()) {
            task.setAnimationUrls(animations);
        }
        if (task.getStatus() == MeshyTaskStatus.FAILED && remote.getTaskError() != null) {
            task.setErrorMessage(remote.getTaskError().getMessage());
        }
    }

    private Map<String, String> buildAnimationUrls(MeshyResultDto result) {
        Map<String, String> urls = new LinkedHashMap<>();
        if (result == null) {
            return urls;
        }
        putIfPresent(urls, "riggedCharacterGlbUrl", result.getRiggedCharacterGlbUrl());
        putIfPresent(urls, "riggedCharacterFbxUrl", result.getRiggedCharacterFbxUrl());
        putIfPresent(urls, "animationGlbUrl", result.getAnimationGlbUrl());
        putIfPresent(urls, "animationFbxUrl", result.getAnimationFbxUrl());
        putIfPresent(urls, "processedUsdzUrl", result.getProcessedUsdzUrl());
        putIfPresent(urls, "processedArmatureFbxUrl", result.getProcessedArmatureFbxUrl());
        putIfPresent(urls, "processedAnimationFpsFbxUrl", result.getProcessedAnimationFpsFbxUrl());
        if (result.getBasicAnimations() != null) {
            var ba = result.getBasicAnimations();
            putIfPresent(urls, "walkingGlbUrl", ba.getWalkingGlbUrl());
            putIfPresent(urls, "walkingFbxUrl", ba.getWalkingFbxUrl());
            putIfPresent(urls, "runningGlbUrl", ba.getRunningGlbUrl());
            putIfPresent(urls, "runningFbxUrl", ba.getRunningFbxUrl());
        }
        return urls;
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private MeshyTaskStatus parseStatus(String raw) {
        try {
            return MeshyTaskStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown Meshy status '{}', keeping IN_PROGRESS", raw);
            return MeshyTaskStatus.IN_PROGRESS;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String shorten(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("data:")) {
            return "data-uri(" + Math.min(value.length(), 32) + "+ chars)";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    /** Resolved source input for derived tasks. */
    private record SourceRef(String inputTaskId, String modelUrl, UUID parentId) {}
}
