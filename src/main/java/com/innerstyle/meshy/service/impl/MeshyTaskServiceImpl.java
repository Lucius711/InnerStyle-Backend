package com.innerstyle.meshy.service.impl;

import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ForbiddenException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.common.exception.UnauthorizedException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.membership.entity.UserMembership;
import com.innerstyle.membership.entity.enums.MembershipStatus;
import com.innerstyle.membership.service.CreditService;
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
import com.innerstyle.meshy.entity.enums.ModelOrigin;
import com.innerstyle.meshy.mapper.MeshyTaskMapper;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.service.ContentModeration;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.util.MeshTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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
    private final CreditService creditService;
    private final ContentModeration contentModeration;

    private static final Set<String> ALLOWED_IMAGE_TYPES =
        Set.of("image/jpeg", "image/jpg", "image/png");

    /** Shared client for streaming model/animation files from the (CORS-less) Meshy CDN. */
    private static final HttpClient MODEL_HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    /** Upper bound for a resize request's target height, in millimetres (sanity guard). */
    private static final double MAX_EXPORT_HEIGHT_MM = 1000.0;

    @Override
    @Transactional
    public MeshyTaskResponse createImageTo3d(ImageTo3dRequest request) {
        return submitImageTo3d(request.getImageUrl(), shorten(request.getImageUrl()),
            request.getAiModel(), request.getShouldTexture(), request.getEnablePbr(),
            request.getShouldRemesh(), request.getTargetPolycount(), request.getTopology(),
            request.getPoseMode(), request.getTexturePrompt(), request.getTextureImageUrl(),
            request.getTargetFormats(), request.getHdTexture(), request.getImageEnhancement());
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
            options.getTargetFormats(), options.getHdTexture(), options.getImageEnhancement());
    }

    @Override
    @Transactional
    public MeshyTaskResponse createFigurinePrototype(FigurineRequest request) {
        ensureConfigured();
        BillingContext billing = beginBilling(MeshyTaskType.FIGURE_PROTOTYPE);
        String meshyTaskId;
        try {
            meshyTaskId = meshyClient.createFigurePrototype(request.getImageUrl());
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.FIGURE_PROTOTYPE, meshyTaskId, null, null);
        task.setSourceImageUrl(shorten(request.getImageUrl()));
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse createFigurinePrototypeFromUpload(MultipartFile file) {
        ensureConfigured();
        String dataUri = toDataUri(file);
        BillingContext billing = beginBilling(MeshyTaskType.FIGURE_PROTOTYPE);
        String meshyTaskId;
        try {
            meshyTaskId = meshyClient.createFigurePrototype(dataUri);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.FIGURE_PROTOTYPE, meshyTaskId, null, null);
        String label = "upload:" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "image");
        task.setSourceImageUrl(label);
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse buildFigurine(FigurineBuildRequest request) {
        ensureConfigured();
        MeshyTask proto = requireSucceeded(request.getSourceTaskId(), MeshyTaskType.FIGURE_PROTOTYPE);
        BillingContext billing = beginBilling(MeshyTaskType.FIGURE_BUILD);
        String meshyTaskId;
        try {
            meshyTaskId = meshyClient.createFigureBuild(proto.getMeshyTaskId());
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.FIGURE_BUILD, meshyTaskId, null, proto.getId());
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse createMultiImageTo3d(MultiImageTo3dRequest request) {
        ensureConfigured();
        BillingContext billing = beginBilling(MeshyTaskType.MULTI_IMAGE_TO_3D);
        String meshyTaskId;
        try {
            var meshyRequest = new MeshyMultiImageTo3dRequest(
                request.getImageUrls(), request.getAiModel(), request.getShouldTexture(),
                request.getEnablePbr(), request.getShouldRemesh(), request.getTargetPolycount(),
                request.getTopology(), request.getTexturePrompt(), request.getTargetFormats(), request.getHdTexture(), request.getImageEnhancement());
            meshyTaskId = meshyClient.createMultiImageTo3d(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.MULTI_IMAGE_TO_3D, meshyTaskId,
            request.getTexturePrompt(), null);
        int count = request.getImageUrls() != null ? request.getImageUrls().size() : 0;
        task.setSourceImageUrl("multi-image:" + count);
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse createMultiImageTo3dFromUpload(List<MultipartFile> files,
                                                            ImageUploadOptions options) {
        ensureConfigured();
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("validation.image.required");
        }
        List<String> dataUris = files.stream().map(this::toDataUri).toList();

        BillingContext billing = beginBilling(MeshyTaskType.MULTI_IMAGE_TO_3D);
        String meshyTaskId;
        try {
            var meshyRequest = new MeshyMultiImageTo3dRequest(
                dataUris, options.getAiModel(), options.getShouldTexture(),
                options.getEnablePbr(), options.getShouldRemesh(), options.getTargetPolycount(),
                options.getTopology(), options.getTexturePrompt(), options.getTargetFormats(), options.getHdTexture(), options.getImageEnhancement());
            meshyTaskId = meshyClient.createMultiImageTo3d(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.MULTI_IMAGE_TO_3D, meshyTaskId,
            options.getTexturePrompt(), null);
        task.setSourceImageUrl("multi-upload:" + files.size());
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    private MeshyTaskResponse submitImageTo3d(String imageUrl, String sourceLabel, String aiModel,
                                              Boolean shouldTexture, Boolean enablePbr, Boolean shouldRemesh,
                                              Integer targetPolycount, String topology, String poseMode,
                                              String texturePrompt, String textureImageUrl,
                                              List<String> targetFormats, Boolean hdTexture,
                                              Boolean imageEnhancement) {
        ensureConfigured();
        contentModeration.assertClean(texturePrompt);
        BillingContext billing = beginBilling(MeshyTaskType.IMAGE_TO_3D);
        String meshyTaskId;
        try {
            var meshyRequest = new MeshyImageTo3dRequest(
                imageUrl, aiModel, shouldTexture, enablePbr, shouldRemesh, targetPolycount,
                topology, poseMode, texturePrompt, textureImageUrl, targetFormats, null, hdTexture, imageEnhancement);
            meshyTaskId = meshyClient.createImageTo3d(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.IMAGE_TO_3D, meshyTaskId, texturePrompt, null);
        task.setSourceImageUrl(sourceLabel);
        applyBilling(task, billing);
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
        contentModeration.assertClean(request.getPrompt());
        BillingContext billing = beginBilling(MeshyTaskType.TEXT_TO_3D_PREVIEW);
        String meshyTaskId;
        try {
            var meshyRequest = MeshyTextTo3dPreviewRequest.preview(
                request.getPrompt(), request.getAiModel(), request.getShouldRemesh(),
                request.getTargetPolycount(), request.getTopology(), request.getPoseMode(),
                request.getTargetFormats(), null);
            meshyTaskId = meshyClient.createTextTo3dPreview(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.TEXT_TO_3D_PREVIEW, meshyTaskId, request.getPrompt(), null);
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse refine(RefineRequest request) {
        ensureConfigured();
        MeshyTask preview = requireSucceeded(request.getSourceTaskId(), MeshyTaskType.TEXT_TO_3D_PREVIEW);
        BillingContext billing = beginBilling(MeshyTaskType.TEXT_TO_3D_REFINE);
        String meshyTaskId;
        try {
            var meshyRequest = MeshyTextTo3dRefineRequest.refine(
                preview.getMeshyTaskId(), request.getEnablePbr(), request.getTexturePrompt(),
                request.getTextureImageUrl(), request.getTargetFormats());
            meshyTaskId = meshyClient.createTextTo3dRefine(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        String prompt = request.getTexturePrompt() != null ? request.getTexturePrompt() : preview.getPrompt();
        MeshyTask task = newTask(MeshyTaskType.TEXT_TO_3D_REFINE, meshyTaskId, prompt, preview.getId());
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse remesh(RemeshRequest request) {
        ensureConfigured();
        SourceRef source = resolveSource(request.getSourceTaskId(), request.getModelUrl(), null);
        BillingContext billing = beginBilling(MeshyTaskType.REMESH);
        String meshyTaskId;
        try {
            var meshyRequest = new MeshyRemeshRequest(
                source.inputTaskId(), source.modelUrl(), request.getTargetFormats(),
                request.getTopology(), request.getTargetPolycount());
            meshyTaskId = meshyClient.createRemesh(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.REMESH, meshyTaskId, null, source.parentId());
        applyBilling(task, billing);
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
        contentModeration.assertClean(request.getTextStylePrompt());
        BillingContext billing = beginBilling(MeshyTaskType.RETEXTURE);
        String meshyTaskId;
        try {
            var meshyRequest = new MeshyRetextureRequest(
                source.inputTaskId(), source.modelUrl(), request.getTextStylePrompt(),
                request.getImageStyleUrl(), request.getAiModel(), request.getEnablePbr(),
                request.getEnableOriginalUv(), request.getTargetFormats());
            meshyTaskId = meshyClient.createRetexture(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.RETEXTURE, meshyTaskId, request.getTextStylePrompt(),
            source.parentId());
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse rig(RigRequest request) {
        ensureConfigured();
        SourceRef source = resolveSource(request.getSourceTaskId(), request.getModelUrl(), null);
        BillingContext billing = beginBilling(MeshyTaskType.RIG);
        String meshyTaskId;
        try {
            var meshyRequest = new MeshyRiggingRequest(
                source.inputTaskId(), source.modelUrl(), request.getHeightMeters(), null);
            meshyTaskId = meshyClient.createRigging(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.RIG, meshyTaskId, null, source.parentId());
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse animate(AnimateRequest request) {
        ensureConfigured();
        MeshyTask rig = requireSucceeded(request.getRigTaskId(), MeshyTaskType.RIG);
        BillingContext billing = beginBilling(MeshyTaskType.ANIMATE);
        String meshyTaskId;
        try {
            MeshyAnimationRequest.PostProcess postProcess = null;
            if (!isBlank(request.getOperationType())) {
                Integer fps = "change_fps".equals(request.getOperationType()) && request.getFps() != null
                    ? Integer.valueOf(request.getFps())
                    : null;
                postProcess = new MeshyAnimationRequest.PostProcess(request.getOperationType(), fps);
            }
            var meshyRequest = new MeshyAnimationRequest(rig.getMeshyTaskId(), request.getActionId(), postProcess);
            meshyTaskId = meshyClient.createAnimation(meshyRequest);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.ANIMATE, meshyTaskId, null, rig.getId());
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskResponse getById(UUID id, UUID userId) {
        MeshyTask task = getTaskOrThrow(id);
        if (task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        return taskMapper.toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeshyTaskResponse> list(UUID userId, MeshyTaskStatus status, Pageable pageable) {
        Page<MeshyTask> page = (status == null)
            ? taskRepository.findByUserId(userId, pageable)
            : taskRepository.findByUserIdAndStatus(userId, status, pageable);
        return page.map(taskMapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        MeshyTask task = getTaskOrThrow(id);
        if (task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        taskRepository.delete(task);
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskService.ModelData fetchModel(UUID id, String format) {
        MeshyTask task = getTaskOrThrow(id);
        return downloadModel(task, normalizeFormat(format));
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskService.ModelData exportModel(UUID id, UUID userId, String format,
            Double heightMm, ModelOrigin origin) {
        MeshyTask task = getTaskOrThrow(id);
        if (task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        String fmt = normalizeFormat(format);

        boolean resize = heightMm != null;
        if (resize) {
            if (heightMm <= 0 || heightMm > MAX_EXPORT_HEIGHT_MM) {
                throw new BadRequestException("meshy.export.heightOutOfRange");
            }
            if (!MeshTransformer.supportsResize(fmt)) {
                throw new BadRequestException("meshy.export.resizeUnsupportedFormat");
            }
            requirePremiumMembership(userId);
        }

        MeshyTaskService.ModelData model = downloadModel(task, fmt);
        if (!resize) {
            return model;
        }
        byte[] resized = MeshTransformer.resize(model.bytes(), fmt, heightMm,
            origin == null ? ModelOrigin.BOTTOM : origin);
        return new MeshyTaskService.ModelData(resized, model.contentType(), model.filename());
    }

    private static String normalizeFormat(String format) {
        return (format == null || format.isBlank()) ? null : format.toLowerCase();
    }

    /** Resolve a task's asset URL and stream its bytes (server-side proxy, avoids CDN CORS). */
    private MeshyTaskService.ModelData downloadModel(MeshyTask task, String fmt) {
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
                log.warn("Model fetch failed ({}) for task {}", resp.statusCode(), task.getId());
                throw new UpstreamServiceException("meshy.upstreamError");
            }
            String contentType = resp.headers().firstValue("content-type")
                .orElseGet(() -> contentTypeFor(fmt));
            return new MeshyTaskService.ModelData(resp.body(), contentType,
                "model." + (fmt != null ? fmt : "glb"));
        } catch (IOException e) {
            log.warn("Model fetch error for task {}: {}", task.getId(), e.getMessage());
            throw new UpstreamServiceException("meshy.upstreamError");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("meshy.upstreamError");
        }
    }

    /** Resizing is a premium feature: require an active, paid membership. */
    private void requirePremiumMembership(UUID userId) {
        UserMembership m = creditService.getOrCreateMembership(userId);
        boolean premium = m.getStatus() == MembershipStatus.ACTIVE
            && m.getPlan() != null && !m.getPlan().isFree();
        if (!premium) {
            throw new ForbiddenException("meshy.export.premiumOnly");
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
        MeshyTaskStatus previous = task.getStatus();
        mergeInto(task, remote);
        taskRepository.save(task);
        log.info("Synced Meshy task {} -> {} ({}%)", task.getMeshyTaskId(), task.getStatus(),
            task.getProgress());
        settleCreditsOnTerminal(task, previous);
    }

    /**
     * When a job first reaches a terminal state, settle credits. Credits are consumed up-front,
     * so a SUCCEEDED job needs nothing; a FAILED/CANCELED job is refunded. The {@code previous}
     * non-terminal check makes this idempotent against duplicate webhook/poll deliveries.
     */
    private void settleCreditsOnTerminal(MeshyTask task, MeshyTaskStatus previous) {
        if (task.getUserId() == null || isTerminal(previous)) {
            return;
        }
        if (task.getStatus() == MeshyTaskStatus.FAILED || task.getStatus() == MeshyTaskStatus.CANCELED) {
            int cost = creditService.creditCost(task.getTaskType().name());
            if (cost > 0) {
                creditService.refund(task.getUserId(), cost, "MESHY_TASK", task.getId());
            }
        }
    }

    private boolean isTerminal(MeshyTaskStatus status) {
        return status == MeshyTaskStatus.SUCCEEDED
            || status == MeshyTaskStatus.FAILED
            || status == MeshyTaskStatus.CANCELED;
    }

    // ------------------------------------------------------------------ helpers

    private void ensureConfigured() {
        if (!properties.hasApiKey()) {
            throw new UpstreamServiceException("meshy.apiKeyMissing");
        }
    }

    // -------------------------------------------------- billing (authorization hold)

    /** Owner + credits consumed for a job (0 when the operation is free). */
    private record BillingContext(UUID userId, int credits) {
    }

    private UUID currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        throw new UnauthorizedException("auth.unauthorized");
    }

    /**
     * Consume membership credits for the job BEFORE calling MeshyAI. If the operation costs no
     * credits it is free. Throws {@code credit.insufficient} if the user is out of credits. The
     * caller must {@link #abortBilling} on a submit failure to refund.
     */
    private BillingContext beginBilling(MeshyTaskType type) {
        UUID userId = currentUserIdOrThrow();
        int cost = creditService.creditCost(type.name());
        if (cost > 0) {
            creditService.consume(userId, type.name(), "MESHY_TASK", null);
        }
        return new BillingContext(userId, cost);
    }

    private void abortBilling(BillingContext ctx) {
        if (ctx != null && ctx.credits() > 0) {
            try {
                creditService.refund(ctx.userId(), ctx.credits(), "MESHY_TASK", null);
            } catch (RuntimeException ex) {
                log.warn("Failed to refund {} credits after submit error: {}",
                    ctx.credits(), ex.getMessage());
            }
        }
    }

    private void applyBilling(MeshyTask task, BillingContext ctx) {
        task.setUserId(ctx.userId());
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
