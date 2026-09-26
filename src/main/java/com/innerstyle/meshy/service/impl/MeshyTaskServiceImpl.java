package com.innerstyle.meshy.service.impl;

import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.exception.AppException;
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
import com.innerstyle.meshy.client.dto.MeshyTextureDto;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dPreviewRequest;
import com.innerstyle.meshy.client.dto.MeshyTextTo3dRefineRequest;
import com.innerstyle.meshy.config.MeshyProperties;
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
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.entity.enums.ModelOrigin;
import com.innerstyle.meshy.entity.MeshyTaskAsset;
import com.innerstyle.meshy.mapper.MeshyTaskMapper;
import com.innerstyle.meshy.entity.MeshyTaskUsdz;
import com.innerstyle.meshy.entity.MeshyTaskThumbnail;
import com.innerstyle.meshy.entity.MeshyTaskTexture;
import com.innerstyle.meshy.entity.MeshyTaskTextureId;
import com.innerstyle.meshy.repository.MeshyTaskAssetRepository;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.repository.MeshyTaskUsdzRepository;
import com.innerstyle.meshy.repository.MeshyTaskThumbnailRepository;
import com.innerstyle.meshy.repository.MeshyTaskTextureRepository;
import com.innerstyle.meshy.service.ContentModeration;
import com.innerstyle.meshy.service.MeshToolRunner;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.util.MeshTransformer;
import com.innerstyle.meshy.util.MeshyStorageKeys;
import com.innerstyle.meshy.util.SsrfGuard;
import com.innerstyle.storage.service.ObjectStorageService;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeshyTaskServiceImpl implements MeshyTaskService {

    private final MeshyClient meshyClient;
    private final MeshyTaskRepository taskRepository;
    private final MeshyTaskAssetRepository assetRepository;
    private final MeshyTaskUsdzRepository usdzRepository;
    private final MeshyTaskThumbnailRepository thumbnailRepository;
    private final MeshyTaskTextureRepository textureRepository;
    private final MeshyTaskMapper taskMapper;
    private final MeshyProperties properties;
    private final CreditService creditService;
    private final ContentModeration contentModeration;
    private final MeshToolRunner meshToolRunner;
    private final ObjectStorageService objectStorage;

    /** Name prefix of Meshy model files cached in dtb_meshy_task_textures (see cachedMeshyModel). */
    private static final String MODEL_CACHE_PREFIX = "model_";

    /** PBR map names served by the texture proxy (see fetchTexture). */
    private static final List<String> TEXTURE_MAPS = List.of("base_color", "metallic", "normal", "roughness", "emission");

    private static final Set<String> ALLOWED_MODEL_EXTS = Set.of("glb", "gltf", "obj", "fbx", "stl");

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    /**
     * Shared client for streaming model/animation/thumbnail files from the
     * (CORS-less) Meshy CDN.
     */
    private static final HttpClient MODEL_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Upper bound for a resize request's target height, in millimetres (sanity
     * guard).
     */
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
                ? file.getOriginalFilename()
                : "image");
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
        contentModeration.assertClean(request.getTexturePrompt());
        BillingContext billing = beginBilling(MeshyTaskType.FIGURE_PROTOTYPE);
        String meshyTaskId;
        try {
            meshyTaskId = meshyClient.createFigurePrototype(request.getImageUrl());
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        // Meshy's figure stages don't accept a texture prompt; we carry the user's
        // desired
        // texture/color down the chain so it can be applied via /retexture after the
        // build.
        MeshyTask task = newTask(MeshyTaskType.FIGURE_PROTOTYPE, meshyTaskId,
                blankToNull(request.getTexturePrompt()), null);
        task.setSourceImageUrl(shorten(request.getImageUrl()));
        applyBilling(task, billing);
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse createFigurinePrototypeFromUpload(MultipartFile file, String texturePrompt) {
        ensureConfigured();
        contentModeration.assertClean(texturePrompt);
        String dataUri = toDataUri(file);
        BillingContext billing = beginBilling(MeshyTaskType.FIGURE_PROTOTYPE);
        String meshyTaskId;
        try {
            meshyTaskId = meshyClient.createFigurePrototype(dataUri);
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.FIGURE_PROTOTYPE, meshyTaskId,
                blankToNull(texturePrompt), null);
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
        // Texture description: explicit value on the build request wins, otherwise
        // inherit the
        // one captured at stage 1. Stored on the build task for a later /retexture
        // step.
        String texturePrompt = blankToNull(request.getTexturePrompt());
        if (texturePrompt == null) {
            texturePrompt = proto.getPrompt();
        }
        contentModeration.assertClean(texturePrompt);
        BillingContext billing = beginBilling(MeshyTaskType.FIGURE_BUILD);
        String meshyTaskId;
        try {
            meshyTaskId = meshyClient.createFigureBuild(proto.getMeshyTaskId());
        } catch (RuntimeException ex) {
            abortBilling(billing);
            throw ex;
        }
        MeshyTask task = newTask(MeshyTaskType.FIGURE_BUILD, meshyTaskId, texturePrompt, proto.getId());
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
                    request.getTopology(), request.getTexturePrompt(), request.getTargetFormats(),
                    request.getHdTexture(), request.getImageEnhancement());
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
                    options.getTopology(), options.getTexturePrompt(), options.getTargetFormats(),
                    options.getHdTexture(), options.getImageEnhancement());
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
                    topology, poseMode, texturePrompt, textureImageUrl, targetFormats, null, hdTexture,
                    imageEnhancement);
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

    /**
     * Convert an uploaded image into a {@code data:<mime>;base64,<...>} URI
     * accepted by Meshy.
     */
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
    @Transactional
    public MeshyTaskResponse importModel(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("validation.model.required");
        }
        String format = modelExtension(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("validation.model.readFailed");
        }
        MeshyTask task = new MeshyTask();
        task.setTaskType(MeshyTaskType.UPLOADED);
        task.setMeshyTaskId("upload-" + UUID.randomUUID()); // no Meshy task; synthetic unique id
        task.setStatus(MeshyTaskStatus.SUCCEEDED);
        task.setProgress(100);
        task.setUserId(userId);
        task.setSourceImageUrl("upload-3d:" + safeName(file.getOriginalFilename()));
        task = taskRepository.save(task);
        storeAsset(task.getId(), bytes, format);
        task.setModelUrls(Map.of(format, selfModelUrl(task.getId())));
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse addBase(UUID taskId, UUID userId, BaseRequest request) {
        MeshyTask task = getTaskOrThrow(taskId);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        if (task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            throw new BadRequestException("meshy.task.notSucceeded");
        }

        // Source the current model (a prior in-place edit/upload wins, else Meshy's
        // GLB).
        Optional<MeshyTaskAsset> existing = assetRepository.findById(task.getId());
        byte[] model;
        String inExt;
        if (existing.isPresent()) {
            model = objectStorage.get(existing.get().getStorageKey());
            inExt = existing.get().getFormat();
        } else {
            model = downloadModel(task, "glb").bytes();
            inExt = "glb";
        }

        MeshToolRunner.SignatureSpec signature = buildSignature(request);
        byte[] withBase = meshToolRunner.addBase(model, inExt, request.shapeOrDefault(),
                request.heightRatioOrDefault(), request.marginRatioOrDefault(), request.colorOrDefault(),
                signature, baseColorTextureBytes(task));

        // Persist in place as GLB; the served model now points at our stored asset.
        storeAsset(task.getId(), withBase, "glb");
        pointModelAtAsset(task);
        invalidateUsdz(task.getId());
        return persistAndMap(task);
    }

    /**
     * Build the engraving spec from a base request: hand-drawn strokes win over
     * typed text.
     */
    private MeshToolRunner.SignatureSpec buildSignature(BaseRequest request) {
        if (request.hasSignatureStrokes()) {
            return new MeshToolRunner.SignatureSpec("strokes", null, request.getSignatureStrokes(),
                    request.signaturePenWidthOrDefault(), request.signatureDepthRatioOrDefault(),
                    request.signatureRaisedOrDefault());
        }
        if (!request.signatureTextOrEmpty().isEmpty()) {
            return new MeshToolRunner.SignatureSpec("text", request.signatureTextOrEmpty(), null,
                    request.signaturePenWidthOrDefault(), request.signatureDepthRatioOrDefault(),
                    request.signatureRaisedOrDefault());
        }
        return null;
    }

    @Override
    @Transactional
    public MeshyTaskResponse removeBase(UUID taskId, UUID userId) {
        MeshyTask task = getTaskOrThrow(taskId);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }

        // Source the current (baked) model; if there's no local asset there's no base
        // to remove.
        Optional<MeshyTaskAsset> existing = assetRepository.findById(task.getId());
        if (existing.isEmpty()) {
            throw new BadRequestException("meshy.base.none");
        }
        byte[] stripped = meshToolRunner.stripBase(objectStorage.get(existing.get().getStorageKey()),
                existing.get().getFormat(), baseColorTextureBytes(task));

        // Persist the base-less model in place (also drops any cached USDZ so AR
        // rebuilds it).
        storeAsset(task.getId(), stripped, "glb");
        pointModelAtAsset(task);
        invalidateUsdz(task.getId());
        return persistAndMap(task);
    }

    @Override
    @Transactional
    public MeshyTaskResponse replaceModel(UUID taskId, UUID userId, byte[] glb) {
        if (glb == null || glb.length == 0) {
            throw new BadRequestException("meshy.model.empty");
        }
        MeshyTask task = getTaskOrThrow(taskId);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        if (task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            throw new BadRequestException("meshy.task.notSucceeded");
        }

        // The browser exported the edited scene (material + transform edits baked in)
        // as a GLB.
        // Store it in place as the authoritative model, repoint the model URL, and drop
        // the stale
        // AR (USDZ) cache so it rebuilds from the edited mesh.
        storeAsset(task.getId(), glb, "glb");
        pointModelAtAsset(task);
        invalidateUsdz(task.getId());
        return persistAndMap(task);
    }

    /**
     * Drop the cached AR (USDZ) for a task after its model changes, so it is
     * rebuilt on demand.
     */
    private void invalidateUsdz(UUID taskId) {
        usdzRepository.findById(taskId).ifPresent(usdz -> {
            objectStorage.deleteAfterCommit(usdz.getStorageKey());
            usdzRepository.delete(usdz);
        });
    }

    @Override
    @Transactional
    public com.innerstyle.meshy.dto.response.RepairResponse repairInPlace(UUID taskId, UUID userId) {
        MeshyTask task = getTaskOrThrow(taskId);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        return repairInPlace(taskId);
    }

    @Override
    @Transactional
    public com.innerstyle.meshy.dto.response.RepairResponse repairInPlace(UUID taskId) {
        MeshyTask task = getTaskOrThrow(taskId);
        if (task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            throw new BadRequestException("meshy.task.notSucceeded");
        }

        Optional<MeshyTaskAsset> existing = assetRepository.findById(task.getId());
        byte[] model;
        String inExt;
        if (existing.isPresent()) {
            model = objectStorage.get(existing.get().getStorageKey());
            inExt = existing.get().getFormat();
        } else {
            model = downloadModel(task, "glb").bytes();
            inExt = "glb";
        }

        // Back up the pre-repair mesh once, before it's overwritten — the only way to
        // offer "revert to original" later, since repair can leave a badly deformed mesh.
        backupOriginalIfMissing(task.getId(), model, inExt);

        MeshToolRunner.RepairOutput out = meshToolRunner.repairToGlb(model, inExt, baseColorTextureBytes(task));

        // Save the repaired mesh in place; the served model now points at our stored
        // asset.
        storeAsset(task.getId(), out.glb(), "glb");
        pointModelAtAsset(task);
        invalidateUsdz(task.getId());
        MeshyTaskResponse updated = persistAndMap(task);
        return new com.innerstyle.meshy.dto.response.RepairResponse(out.before(), out.after(), updated, true);
    }

    @Override
    @Transactional
    public com.innerstyle.meshy.dto.response.RepairResponse revertToOriginal(UUID taskId, UUID userId) {
        MeshyTask task = getTaskOrThrow(taskId);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        return revertToOriginal(taskId);
    }

    @Override
    @Transactional
    public com.innerstyle.meshy.dto.response.RepairResponse revertToOriginal(UUID taskId) {
        MeshyTask task = getTaskOrThrow(taskId);
        MeshyTaskAsset asset = assetRepository.findById(taskId)
            .filter(a -> a.getOriginalStorageKey() != null)
            .orElseThrow(() -> new BadRequestException("meshy.task.noOriginalBackup"));
        byte[] current = objectStorage.get(asset.getStorageKey());
        byte[] original = objectStorage.get(asset.getOriginalStorageKey());
        String originalFormat = asset.getOriginalFormat();

        com.innerstyle.meshy.dto.response.PrintabilityResponse before =
            meshToolRunner.analyze(current, asset.getFormat());

        // Point the current model back at the backup object itself (no copy): storageKey ==
        // originalStorageKey is what "not repaired" means, so the revert button hides. The backup
        // is kept, so the user can re-repair and revert again freely.
        if (!asset.getStorageKey().equals(asset.getOriginalStorageKey())) {
            objectStorage.deleteAfterCommit(asset.getStorageKey());
        }
        asset.setStorageKey(asset.getOriginalStorageKey());
        asset.setFormat(originalFormat);
        asset.setContentType(asset.getOriginalContentType());
        asset.setSize(asset.getOriginalSize());
        assetRepository.save(asset);
        pointModelAtAsset(task);
        invalidateUsdz(taskId);
        MeshyTaskResponse updated = persistAndMap(task);

        com.innerstyle.meshy.dto.response.PrintabilityResponse after =
            meshToolRunner.analyze(original, originalFormat);
        return new com.innerstyle.meshy.dto.response.RepairResponse(before, after, updated, false);
    }

    /**
     * Point the task's served model at our stored GLB asset (same-origin proxy
     * URL).
     */
    private void pointModelAtAsset(MeshyTask task) {
        Map<String, String> urls = new LinkedHashMap<>(
                task.getModelUrls() != null ? task.getModelUrls() : Map.of());
        urls.put("glb", selfModelUrl(task.getId()));
        task.setModelUrls(urls);
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskResponse getById(UUID id, UUID userId) {
        MeshyTask task = getTaskOrThrow(id);
        // Legacy tasks (userId == null) have no ownership record — allow any
        // authenticated user to
        // access them rather than locking them out permanently.
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        MeshyTaskResponse response = taskMapper.toResponse(task);
        response.setHasOriginalBackup(assetRepository.isRevertible(id));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeshyTaskResponse> list(UUID userId, MeshyTaskStatus status, Pageable pageable) {
        // Include legacy rows (userId IS NULL) so pre-auth tasks aren't invisible to
        // the user.
        Page<MeshyTask> page = (status == null)
                ? taskRepository.findByUserIdOrLegacy(userId, pageable)
                : taskRepository.findByUserIdOrLegacyAndStatus(userId, status, pageable);
        return page.map(taskMapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        MeshyTask task = getTaskOrThrow(id);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        // Soft delete: mark deletedAt instead of physically removing the row.
        // Use deleteTask(id, userId, hardDelete=true) or a DB query for permanent
        // removal.
        task.setDeletedAt(java.time.Instant.now());
        taskRepository.save(task);
    }

    @Override
    @Transactional(readOnly = true)
    public String modelETag(UUID id, String format) {
        MeshyTask task = getTaskOrThrow(id);
        String fmt = normalizeFormat(format);
        // Stored objects get a fresh key on every write, so the key is the content version;
        // Meshy-hosted results only change with the task (updatedAt).
        String version = "usdz".equals(fmt)
                ? usdzRepository.findById(id).map(MeshyTaskUsdz::getStorageKey).orElse("")
                : assetRepository.findById(id).map(MeshyTaskAsset::getStorageKey).orElse("");
        return "\"" + id + "-" + fmt + "-" + Integer.toHexString((version + task.getUpdatedAt()).hashCode()) + "\"";
    }

    @Override
    @Transactional
    public MeshyTaskService.ModelData fetchModel(UUID id, String format) {
        MeshyTask task = getTaskOrThrow(id);
        String fmt = normalizeFormat(format);

        // iOS AR Quick Look (usdz) needs a real .usdz file — never the stored GLB.
        // Prefer the
        // cached browser-built USDZ, then a Meshy-hosted one; otherwise 404 so the
        // frontend builds
        // and uploads it (see storeUsdz).
        if ("usdz".equals(fmt)) {
            return fetchUsdz(task);
        }

        // A locally-stored model (uploaded file or in-place edit such as an added base)
        // is the
        // authoritative current model — serve it directly.
        Optional<MeshyTaskAsset> asset = assetRepository.findById(id);
        if (asset.isPresent()) {
            MeshyTaskAsset a = asset.get();
            return new MeshyTaskService.ModelData(objectStorage.get(a.getStorageKey()), a.getContentType(),
                    "model." + a.getFormat());
        }
        return cachedMeshyModel(task, fmt);
    }

    /**
     * Serve a Meshy-hosted model from R2, downloading it from the Meshy CDN only on the first
     * request (or if Meshy's file path changed). Meshy CDN links are presigned and the task is
     * purged after ~14 days, so the copy in R2 is what keeps old models viewable.
     * ponytail: reuses dtb_meshy_task_textures (a generic per-task remote-file cache keyed by
     * name) with a {@code model_<ext>} name instead of a dedicated table; split it out if models
     * ever need their own columns.
     */
    private MeshyTaskService.ModelData cachedMeshyModel(MeshyTask task, String fmt) {
        String url = resolveAssetUrl(task, fmt);
        if (url == null) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        // Key on the resolved file's extension, never the raw ?format= (public, caller-controlled).
        String ext = textureExt(url);
        String name = MODEL_CACHE_PREFIX + ext;
        String sourceKey = stripQuery(url);
        MeshyTaskTexture cached = textureRepository.findById(new MeshyTaskTextureId(task.getId(), name))
                .orElse(null);
        if (cached != null && cached.getSourceKey().equals(sourceKey)) {
            return new MeshyTaskService.ModelData(objectStorage.get(cached.getStorageKey()),
                    cached.getContentType(), "model." + cached.getExt());
        }
        MeshyTaskService.ModelData fresh = downloadModel(task, fmt);
        storeTexture(task.getId(), name, sourceKey, fresh.bytes(), fresh.contentType(), ext);
        return fresh;
    }

    /**
     * On a task's first transition into SUCCEEDED, copy its files into R2 while the Meshy links
     * are still valid. Best-effort: a failure here only means the first view downloads it instead.
     * ponytail: runs inside the webhook/poll transaction (multi-MB download); move to an async
     * job if webhook latency ever matters.
     */
    private void cacheMeshyAssetsOnSuccess(MeshyTask task, MeshyTaskStatus previous) {
        if (isTerminal(previous) || task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            return;
        }
        copyMeshyAssetsToR2(task);
    }

    /**
     * Copy a Meshy task's model (unless a local/edited model already exists or it is already
     * cached) and all its PBR maps into R2. Meshy can purge a task within days, after which its
     * files are gone for good. Returns false when the model could not be copied.
     */
    private boolean copyMeshyAssetsToR2(MeshyTask task) {
        boolean modelOk = assetRepository.existsById(task.getId()) || copyMeshyModelToR2(task);
        for (String map : TEXTURE_MAPS) {
            try {
                fetchTexture(task.getId(), map); // caches into R2; a cache hit costs no download
            } catch (AppException e) {
                // map not produced for this task (404) or not downloadable — nothing to keep
            } catch (RuntimeException e) {
                log.warn("Could not store texture {} of task {} in R2: {}", map, task.getId(), e.getMessage());
            }
        }
        return modelOk;
    }

    private boolean copyMeshyModelToR2(MeshyTask task) {
        String url = resolveAssetUrl(task, null);
        if (url == null) {
            return true; // nothing hosted by Meshy to copy
        }
        String ext = textureExt(url);
        String name = MODEL_CACHE_PREFIX + ext;
        boolean alreadyCached = textureRepository.findById(new MeshyTaskTextureId(task.getId(), name))
                .map(c -> c.getSourceKey().equals(stripQuery(url)))
                .orElse(false);
        if (alreadyCached) {
            return true;
        }
        // Raw fetch, not downloadModel(): its 403/404 handling marks the task EXPIRED, which a
        // best-effort copy must never do.
        byte[] bytes = tryFetchBytes(url);
        if (bytes == null) {
            log.warn("Could not download Meshy model of task {} for R2", task.getId());
            return false;
        }
        try {
            storeTexture(task.getId(), name, stripQuery(url), bytes, contentTypeFor(ext), ext);
            return true;
        } catch (RuntimeException e) { // R2 down must not roll back the caller's transaction
            log.warn("Could not store Meshy model of task {} in R2: {}", task.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> r2BackfillCandidates() {
        return taskRepository.findR2BackfillCandidates();
    }

    @Override
    @Transactional
    public R2BackfillResult backfillToR2(UUID id) {
        MeshyTask task = getTaskOrThrow(id);
        try {
            // Stored links are signed and short-lived: pull fresh ones (applyRemoteState saves them).
            applyRemoteState(meshyClient.getTask(task.getTaskType(), task.getMeshyTaskId()));
        } catch (ResourceNotFoundException e) {
            return R2BackfillResult.PURGED;
        } catch (RuntimeException e) {
            log.warn("R2 backfill: Meshy lookup failed for task {}: {}", id, e.getMessage());
            return R2BackfillResult.FAILED;
        }
        return copyMeshyAssetsToR2(task) ? R2BackfillResult.COPIED : R2BackfillResult.FAILED;
    }

    /**
     * Resolve a task's USDZ: cached (browser-built) first, then a Meshy-hosted one,
     * else 404.
     */
    private MeshyTaskService.ModelData fetchUsdz(MeshyTask task) {
        Optional<MeshyTaskUsdz> cached = usdzRepository.findById(task.getId());
        if (cached.isPresent()) {
            return new MeshyTaskService.ModelData(
                    objectStorage.get(cached.get().getStorageKey()), MeshyStorageKeys.USDZ_CONTENT_TYPE,
                    "model.usdz");
        }
        Map<String, String> models = task.getModelUrls();
        if (models != null && models.get("usdz") != null) {
            return downloadModel(task, "usdz");
        }
        throw new ResourceNotFoundException("meshy.task.notFound");
    }

    @Override
    @Transactional
    public void storeUsdz(UUID id, UUID userId, byte[] data) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("meshy.usdz.empty");
        }
        MeshyTask task = getTaskOrThrow(id);
        // Ownership guard (finding M1): only the task owner may cache its AR asset. 404
        // (not 403)
        // on mismatch so a valid task id is not confirmed to a non-owner.
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        MeshyTaskUsdz usdz = usdzRepository.findById(task.getId()).orElseGet(MeshyTaskUsdz::new);
        objectStorage.deleteAfterCommit(usdz.getStorageKey());
        usdz.setTaskId(task.getId());
        usdz.setStorageKey(objectStorage.put(MeshyStorageKeys.usdz(task.getId()), MeshyStorageKeys.USDZ_EXT, data,
                MeshyStorageKeys.USDZ_CONTENT_TYPE));
        usdz.setSize(data.length);
        usdzRepository.save(usdz);
    }

    @Override
    @Transactional
    public void storeThumbnail(UUID id, UUID userId, byte[] data) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("meshy.thumbnail.empty");
        }
        MeshyTask task = getTaskOrThrow(id);
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        saveThumbnail(task.getId(), data);
        // Point the task at our stored image so listings/detail show the edited model.
        task.setThumbnailUrl(selfThumbnailUrl(task.getId()));
        taskRepository.save(task);
    }

    @Override
    @Transactional
    public byte[] fetchThumbnailImage(UUID id) {
        byte[] cached = thumbnailRepository.findById(id)
                .map(MeshyTaskThumbnail::getStorageKey)
                .map(objectStorage::get)
                .orElse(null);
        if (cached != null && cached.length > 0) {
            return cached;
        }
        MeshyTask task = taskRepository.findById(id).orElse(null);
        if (task == null) {
            return null;
        }
        // Try the stored preview URL first (still valid for recent tasks); if it's
        // gone/expired,
        // re-fetch a fresh signed URL from Meshy. Cache the bytes so we never hit the
        // CDN again.
        byte[] bytes = fetchImageBytes(externalHttpUrl(task.getThumbnailUrl()));
        boolean refetchedFromMeshy = false;
        if ((bytes == null || bytes.length == 0) && task.getMeshyTaskId() != null
                && !task.getMeshyTaskId().startsWith("upload-")) {
            try {
                MeshyTaskDto remote = meshyClient.getTask(task.getTaskType(), task.getMeshyTaskId());
                if (remote != null) {
                    bytes = fetchImageBytes(externalHttpUrl(remote.getThumbnailUrl()));
                    refetchedFromMeshy = bytes != null && bytes.length > 0;
                }
            } catch (ResourceNotFoundException e) {
                // Meshy returned 404 "Task not found" — task was purged. Mark as EXPIRED.
                log.warn("Task {} purged by Meshy (404); marking EXPIRED", task.getMeshyTaskId());
                task.setStatus(MeshyTaskStatus.EXPIRED);
                task.setErrorMessage("meshy.task.purgedByMeshy");
                taskRepository.save(task);
            } catch (RuntimeException e) {
                log.warn("Thumbnail re-fetch from Meshy failed for task {}: {}", id, e.getMessage());
            }
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        saveThumbnail(id, bytes);
        // If we re-fetched from Meshy and the task's stored URL is still an expiring
        // CDN link,
        // point it at our own proxy now so subsequent webhooks/polls don't overwrite
        // it.
        if (refetchedFromMeshy) {
            String self = selfThumbnailUrl(id);
            if (!self.equals(task.getThumbnailUrl())) {
                task.setThumbnailUrl(self);
                taskRepository.save(task);
            }
        }
        return bytes;
    }

    /**
     * Download and store the preview image for a task (idempotent — skips if
     * already cached).
     * Returns true when a thumbnail is available locally afterwards.
     */
    private boolean cacheThumbnailBytes(UUID taskId, String url) {
        if (taskId == null) {
            return false;
        }
        if (thumbnailRepository.existsById(taskId)) {
            return true;
        }
        byte[] bytes = fetchImageBytes(externalHttpUrl(url));
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        saveThumbnail(taskId, bytes);
        return true;
    }

    /** Upload a task's preview image to R2 and point its thumbnail row at it (old object dropped). */
    private void saveThumbnail(UUID taskId, byte[] bytes) {
        MeshyTaskThumbnail thumb = thumbnailRepository.findById(taskId).orElseGet(MeshyTaskThumbnail::new);
        objectStorage.deleteAfterCommit(thumb.getStorageKey());
        thumb.setTaskId(taskId);
        thumb.setStorageKey(objectStorage.put(MeshyStorageKeys.thumbnail(taskId), MeshyStorageKeys.THUMBNAIL_EXT,
                bytes, MeshyStorageKeys.THUMBNAIL_CONTENT_TYPE));
        thumb.setSize(bytes.length);
        thumbnailRepository.save(thumb);
    }

    /**
     * Return the URL only if it's an absolute http(s) URL (a remote CDN), else
     * null.
     */
    private String externalHttpUrl(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        return (u.startsWith("http://") || u.startsWith("https://")) ? u : null;
    }

    /**
     * Download image bytes from a URL; returns null on any failure or non-2xx
     * response.
     */
    private byte[] fetchImageBytes(String url) {
        if (url == null) {
            return null;
        }
        try {
            HttpResponse<byte[]> resp = MODEL_HTTP.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                return null;
            }
            byte[] body = resp.body();
            return (body != null && body.length > 0) ? body : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    @Transactional
    public MeshyTaskService.ModelData fetchTexture(UUID id, String map) {
        MeshyTask task = getTaskOrThrow(id);
        List<MeshyTextureDto> textures = task.getTextureUrls();
        if (textures == null || textures.isEmpty()) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        MeshyTextureDto tex = textures.get(0);
        String key = (map == null || map.isBlank()) ? "base_color" : map.toLowerCase();
        String url = switch (key) {
            case "metallic" -> tex.getMetallic();
            case "normal" -> tex.getNormal();
            case "roughness" -> tex.getRoughness();
            case "emission" -> tex.getEmission();
            default -> tex.getBaseColor();
        };
        if (url == null || url.isBlank()) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }

        // Cache lookup. Meshy CDN URLs are presigned and expire, so we key the cache on
        // the URL
        // path (without the signing query) — a stable fingerprint of the underlying
        // image. A cache
        // hit with a matching source key serves the stored bytes and skips the
        // (expiry-prone) CDN.
        String sourceKey = stripQuery(url);
        MeshyTaskTexture cached = textureRepository
                .findById(new MeshyTaskTextureId(id, key))
                .orElse(null);
        if (cached != null && cached.getSourceKey().equals(sourceKey)) {
            return new MeshyTaskService.ModelData(
                    objectStorage.get(cached.getStorageKey()), cached.getContentType(), "texture." + cached.getExt());
        }

        byte[] bytes = tryFetchBytes(url);
        if (bytes == null && cached == null) {
            // Expired signature on a task Meshy still keeps (~14 days): ask Meshy for fresh URLs
            // (applyRemoteState stores them), then retry once. Same recovery as the thumbnail proxy.
            String fresh = refreshedTextureUrl(task, key);
            if (fresh != null) {
                url = fresh;
                sourceKey = stripQuery(url);
                bytes = tryFetchBytes(url);
            }
            if (bytes == null) {
                log.warn("Texture {} of task {} still unavailable after Meshy refresh (fresh url: {})",
                        key, id, fresh != null);
            }
        }
        if (bytes == null) {
            // CDN fetch failed (commonly an expired signature). Serve any stale cache we
            // have
            // rather than letting the viewer fall back to a grey, un-textured model.
            if (cached != null) {
                log.warn("Texture fetch failed for task {} map {}; serving stale cache", id, key);
                return new MeshyTaskService.ModelData(objectStorage.get(cached.getStorageKey()),
                        cached.getContentType(), "texture." + cached.getExt());
            }
            throw new UpstreamServiceException("meshy.upstreamError");
        }

        String ext = textureExt(url);
        String contentType = "image/" + ("jpg".equals(ext) ? "jpeg" : ext);
        storeTexture(id, key, sourceKey, bytes, contentType, ext);
        return new MeshyTaskService.ModelData(bytes, contentType, "texture." + ext);
    }

    /** Re-fetch the task from Meshy and return the new signed URL of one map, or null. */
    private String refreshedTextureUrl(MeshyTask task, String map) {
        if (task.getMeshyTaskId() == null || task.getMeshyTaskId().startsWith("upload-")) {
            return null;
        }
        try {
            applyRemoteState(meshyClient.getTask(task.getTaskType(), task.getMeshyTaskId()));
        } catch (RuntimeException e) {
            log.warn("Texture URL refresh from Meshy failed for task {}: {}", task.getId(), e.getMessage());
            return null;
        }
        List<MeshyTextureDto> textures = task.getTextureUrls();
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        MeshyTextureDto tex = textures.get(0);
        String url = switch (map) {
            case "metallic" -> tex.getMetallic();
            case "normal" -> tex.getNormal();
            case "roughness" -> tex.getRoughness();
            case "emission" -> tex.getEmission();
            default -> tex.getBaseColor();
        };
        return (url == null || url.isBlank()) ? null : url;
    }

    /** Insert/replace the cached bytes for one of a task's texture maps. */
    private void storeTexture(UUID taskId, String mapName, String sourceKey, byte[] bytes,
            String contentType, String ext) {
        MeshyTaskTexture texture = textureRepository
                .findById(new MeshyTaskTextureId(taskId, mapName))
                .orElseGet(MeshyTaskTexture::new);
        objectStorage.deleteAfterCommit(texture.getStorageKey());
        texture.setTaskId(taskId);
        texture.setMapName(mapName);
        texture.setSourceKey(sourceKey);
        texture.setContentType(contentType);
        texture.setExt(ext);
        texture.setStorageKey(objectStorage.put(MeshyStorageKeys.texture(taskId, mapName), ext, bytes, contentType));
        texture.setSize(bytes.length);
        textureRepository.save(texture);
    }

    /** The part of a URL before its query string (drops presigning params). */
    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    @Override
    @Transactional(readOnly = true)
    public ExportPrep prepareUserExport(UUID id, UUID userId, String format, Double heightMm,
            ModelOrigin origin) {
        MeshyTask task = getTaskOrThrow(id);
        if (task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        // Locally-stored model (uploaded / in-place edited): export its bytes directly.
        Optional<MeshyTaskAsset> asset = assetRepository.findById(id);
        if (asset.isPresent()) {
            MeshyTaskAsset a = asset.get();
            byte[] data = objectStorage.get(a.getStorageKey());
            if (heightMm != null) {
                if (heightMm <= 0 || heightMm > MAX_EXPORT_HEIGHT_MM) {
                    throw new BadRequestException("meshy.export.heightOutOfRange");
                }
                if (!MeshTransformer.supportsResize(a.getFormat())) {
                    throw new BadRequestException("meshy.export.resizeUnsupportedFormat");
                }
                requirePremiumMembership(userId);
                data = MeshTransformer.resize(data, a.getFormat(), heightMm,
                        origin == null ? ModelOrigin.BOTTOM : origin);
            }
            return new ExportPrep(task, a.getFormat(), data);
        }
        String fmt = normalizeFormat(format);
        byte[] resized = null;
        if (heightMm != null) {
            if (heightMm <= 0 || heightMm > MAX_EXPORT_HEIGHT_MM) {
                throw new BadRequestException("meshy.export.heightOutOfRange");
            }
            if (!MeshTransformer.supportsResize(fmt)) {
                throw new BadRequestException("meshy.export.resizeUnsupportedFormat");
            }
            requirePremiumMembership(userId);
            MeshyTaskService.ModelData model = downloadModel(task, fmt);
            resized = MeshTransformer.resize(model.bytes(), fmt, heightMm,
                    origin == null ? ModelOrigin.BOTTOM : origin);
        }
        return new ExportPrep(task, fmt, resized);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportPrep prepareTask(UUID taskId, String format) {
        MeshyTask task = getTaskOrThrow(taskId);
        Optional<MeshyTaskAsset> asset = assetRepository.findById(taskId);
        if (asset.isPresent()) {
            MeshyTaskAsset a = asset.get();
            return new ExportPrep(task, a.getFormat(), objectStorage.get(a.getStorageKey()));
        }
        return new ExportPrep(task, normalizeFormat(format), null);
    }

    @Override
    public void writeZip(ExportPrep prep, OutputStream out) throws IOException {
        MeshyTask task = prep.task();
        String fmt = prep.fmt();
        String modelUrl = prep.resizedModel() == null ? resolveAssetUrl(task, fmt) : null;
        if (prep.resizedModel() == null && modelUrl == null) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        // Don't close `out`: the servlet container owns the response stream.
        ZipOutputStream zip = new ZipOutputStream(out);
        // 3D meshes and texture images are already compressed; favour speed over ratio.
        zip.setLevel(Deflater.BEST_SPEED);

        String ext = fmt != null ? fmt : "glb";
        List<MeshyTextureDto> textures = task.getTextureUrls();
        MeshyTextureDto tex0 = (textures != null && !textures.isEmpty()) ? textures.get(0) : null;
        boolean multi = textures != null && textures.size() > 1;
        // .obj is geometry only — without an .mtl the colour won't apply, so we
        // generate one and
        // bind it into the .obj. Other formats (glb embeds textures) are streamed
        // as-is.
        boolean objWithColor = "obj".equals(ext) && tex0 != null;
        String texDir = multi ? "textures/set1/" : "textures/";
        String matName = "innerstyle";

        zip.putNextEntry(new ZipEntry("model." + ext));
        if (objWithColor) {
            byte[] objBytes = prep.resizedModel() != null
                    ? prep.resizedModel()
                    : fetchBytesOrThrow(modelUrl);
            zip.write(bindMtlToObj(objBytes, "model.mtl", matName));
        } else if (prep.resizedModel() != null) {
            zip.write(prep.resizedModel());
        } else {
            streamRemoteInto(modelUrl, zip);
        }
        zip.closeEntry();

        if (objWithColor) {
            writeMtl(zip, matName, tex0, texDir);
        }

        if (textures != null && !textures.isEmpty()) {
            int set = 1;
            for (MeshyTextureDto tex : textures) {
                String dir = multi ? "textures/set" + set + "/" : "textures/";
                addTexture(zip, dir + "base_color", tex.getBaseColor());
                addTexture(zip, dir + "metallic", tex.getMetallic());
                addTexture(zip, dir + "normal", tex.getNormal());
                addTexture(zip, dir + "roughness", tex.getRoughness());
                addTexture(zip, dir + "emission", tex.getEmission());
                set++;
            }
        }
        zip.finish();
    }

    /**
     * Drop any stale mtllib/usemtl and bind a single generated material to the
     * whole OBJ.
     */
    private static byte[] bindMtlToObj(byte[] objBytes, String mtlFile, String matName) {
        String obj = new String(objBytes, StandardCharsets.UTF_8);
        obj = obj.replaceAll("(?m)^\\s*mtllib.*\\R?", "");
        obj = obj.replaceAll("(?m)^\\s*usemtl.*\\R?", "");
        Matcher m = Pattern.compile("(?m)^f\\s").matcher(obj);
        String usemtl = "usemtl " + matName + "\n";
        String body = m.find()
                ? obj.substring(0, m.start()) + usemtl + obj.substring(m.start())
                : usemtl + obj;
        return ("mtllib " + mtlFile + "\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    /** Generate a classic + PBR .mtl pointing at the exported texture maps. */
    private void writeMtl(ZipOutputStream zip, String matName, MeshyTextureDto tex, String texDir)
            throws IOException {
        StringBuilder sb = new StringBuilder("# InnerStyle export\n");
        sb.append("newmtl ").append(matName).append("\n");
        sb.append("Ka 1.000 1.000 1.000\n");
        sb.append("Kd 1.000 1.000 1.000\n");
        sb.append("Ks 0.000 0.000 0.000\n");
        sb.append("d 1.0\n");
        sb.append("illum 2\n");
        appendMap(sb, "map_Kd", texDir, "base_color", tex.getBaseColor());
        appendMap(sb, "map_Bump", texDir, "normal", tex.getNormal());
        appendMap(sb, "map_Pr", texDir, "roughness", tex.getRoughness());
        appendMap(sb, "map_Pm", texDir, "metallic", tex.getMetallic());
        appendMap(sb, "map_Ke", texDir, "emission", tex.getEmission());
        zip.putNextEntry(new ZipEntry("model.mtl"));
        zip.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void appendMap(StringBuilder sb, String key, String dir, String name, String url) {
        if (url != null && !url.isBlank()) {
            sb.append(key).append(' ').append(dir).append(name).append('.').append(textureExt(url)).append('\n');
        }
    }

    private byte[] fetchBytesOrThrow(String url) {
        byte[] bytes = tryFetchBytes(url);
        if (bytes == null) {
            throw new UpstreamServiceException("meshy.upstreamError");
        }
        return bytes;
    }

    /**
     * Stream a remote file straight into the open zip entry (no full in-memory
     * copy).
     */
    private void streamRemoteInto(String url, OutputStream target) throws IOException {
        try {
            HttpResponse<InputStream> resp = MODEL_HTTP.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(120))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Model fetch for zip failed ({}) for {}", resp.statusCode(), url);
                throw new UpstreamServiceException("meshy.upstreamError");
            }
            try (InputStream in = resp.body()) {
                in.transferTo(target);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while streaming model", e);
        }
    }

    /**
     * Add one texture map to the zip. Textures are small, so we buffer and skip on
     * failure.
     */
    private void addTexture(ZipOutputStream zip, String baseName, String url) throws IOException {
        if (url == null || url.isBlank()) {
            return;
        }
        byte[] bytes = tryFetchBytes(url);
        if (bytes == null) {
            return;
        }
        zip.putNextEntry(new ZipEntry(baseName + "." + textureExt(url)));
        zip.write(bytes);
        zip.closeEntry();
    }

    /**
     * Fetch a task's base-color texture map bytes (Meshy's "color" output), or null
     * when the task
     * has no texture or the download fails. Passed to the mesh toolchain so base
     * add/strip ops
     * re-embed the map that Meshy only references externally (otherwise the figure
     * exports grey).
     */
    private byte[] baseColorTextureBytes(MeshyTask task) {
        // Go through the R2-cached texture proxy: Meshy's CDN URL is presigned and expires, so a
        // raw fetch returns 403 on older tasks and the mesh op would export an untextured model.
        try {
            return fetchTexture(task.getId(), "base_color").bytes();
        } catch (AppException e) {
            return null; // no base-color map (or CDN + cache both unavailable)
        }
    }

    private byte[] tryFetchBytes(String url) {
        try {
            HttpResponse<byte[]> resp = MODEL_HTTP.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return resp.statusCode() / 100 == 2 ? resp.body() : null;
        } catch (IOException e) {
            log.warn("Skipping texture {} in zip: {}", url, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String textureExt(String url) {
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int dot = path.lastIndexOf('.');
        String ext = dot >= 0 ? path.substring(dot + 1) : "png";
        return ext.length() >= 1 && ext.length() <= 5 ? ext.toLowerCase() : "png";
    }

    private static String normalizeFormat(String format) {
        return (format == null || format.isBlank()) ? null : format.toLowerCase();
    }

    /**
     * Resolve a task's asset URL and stream its bytes (server-side proxy, avoids
     * CDN CORS).
     */
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
                // 403/404 on Meshy CDN = signed URL expired or task purged by Meshy.
                // Mark as EXPIRED so the user library stops showing this task.
                if (resp.statusCode() == 403 || resp.statusCode() == 404) {
                    task.setStatus(MeshyTaskStatus.EXPIRED);
                    task.setErrorMessage("meshy.task.expired");
                    taskRepository.save(task);
                    throw new ResourceNotFoundException("meshy.task.expired");
                }
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
     * Pick the best asset URL. Prefers an explicitly requested format, then a
     * web-friendly
     * .glb/.gltf among ALL outputs (so the in-browser viewer can play animations
     * from
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

    /**
     * First URL whose path (ignoring query string) ends with the given extension.
     */
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
        cacheMeshyAssetsOnSuccess(task, previous);
        autoStripFigureBase(task, previous);
    }

    /**
     * Meshy's Figure builder always stands the figure on a pedestal. When a figure
     * build first
     * reaches SUCCEEDED, strip that baked-in base so the stored model ships
     * base-less — users add
     * their own base later in the editor. Best-effort: any failure (or no
     * detectable base) leaves
     * Meshy's original model untouched, so a figure is never broken by this step.
     */
    private void autoStripFigureBase(MeshyTask task, MeshyTaskStatus previous) {
        if (isTerminal(previous) || task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            return; // only act on the first transition into SUCCEEDED
        }
        if (task.getTaskType() != MeshyTaskType.FIGURE_BUILD) {
            return;
        }
        if (assetRepository.existsById(task.getId())) {
            return; // a local/edited asset already exists — never clobber it
        }
        try {
            byte[] glb = downloadModel(task, "glb").bytes();
            byte[] stripped = meshToolRunner.stripGeneratedBase(glb, "glb", baseColorTextureBytes(task));
            storeAsset(task.getId(), stripped, "glb");
            pointModelAtAsset(task);
            invalidateUsdz(task.getId());
            taskRepository.save(task);
            log.info("Stripped Meshy figure base for task {}", task.getId());
        } catch (Exception e) { // keep Meshy's original model on any failure
            log.warn("Auto base-strip skipped for figure task {}: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * When a job first reaches a terminal state, settle credits. Credits are
     * consumed up-front,
     * so a SUCCEEDED job needs nothing; a FAILED/CANCELED job is refunded. The
     * {@code previous}
     * non-terminal check makes this idempotent against duplicate webhook/poll
     * deliveries.
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

    // -------------------------------------------------- billing (authorization
    // hold)

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
     * Consume membership credits for the job BEFORE calling MeshyAI. If the
     * operation costs no
     * credits it is free. Throws {@code credit.insufficient} if the user is out of
     * credits. The
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

    /**
     * Resolve a SUCCEEDED prior task of a specific type and return its entity. Only
     * the owning
     * user may use their own task as a pipeline source (finding CAO-2: this used to
     * skip the
     * ownership check that every other task-accessing method enforces, letting any
     * authenticated
     * user chain remesh/retexture/rig/refine/animate/figurine-build off someone
     * else's task).
     */
    private MeshyTask requireSucceeded(UUID id, MeshyTaskType expectedType) {
        MeshyTask task = getTaskOrThrow(id);
        UUID userId = currentUserIdOrThrow();
        if (task.getUserId() != null && !task.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("meshy.task.notFound");
        }
        if (expectedType != null && task.getTaskType() != expectedType) {
            throw new BadRequestException("meshy.task.notFound");
        }
        if (task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            throw new BadRequestException("meshy.task.notSucceeded");
        }
        return task;
    }

    /**
     * Resolve a source: either our prior task id (-> Meshy input_task_id + parent
     * link) or a model URL.
     * Exactly one must be provided.
     *
     * <p>
     * Native pipeline tasks (image/multi-image/text-to-3D, remesh, retexture) are
     * valid Meshy
     * {@code input_task_id} values and continue by id. Creative Lab outputs (e.g.
     * the chibi
     * figurine build) are NOT accepted as {@code input_task_id} by
     * remesh/retexture/rigging, so we
     * continue the pipeline from their generated model URL instead — same
     * downstream flow as
     * image-to-3D, just sourced by URL. The parent link is preserved either way for
     * lineage.
     */
    private SourceRef resolveSource(UUID sourceTaskId, String modelUrl, MeshyTaskType expectedType) {
        boolean hasTask = sourceTaskId != null;
        boolean hasUrl = !isBlank(modelUrl);
        if (hasTask == hasUrl) {
            throw new BadRequestException("validation.sourceTaskId.required");
        }
        if (hasTask) {
            MeshyTask source = requireSucceeded(sourceTaskId, expectedType);
            // Locally-stored model (uploaded file or in-place edit): send it to Meshy as a
            // data URI.
            Optional<MeshyTaskAsset> asset = assetRepository.findById(source.getId());
            if (asset.isPresent()) {
                return new SourceRef(null, toModelDataUri(objectStorage.get(asset.get().getStorageKey())), source.getId());
            }
            if (acceptedAsInputTask(source.getTaskType())) {
                return new SourceRef(source.getMeshyTaskId(), null, source.getId());
            }
            // Creative Lab / non-native source: continue from its generated model file.
            String glb = resolveAssetUrl(source, "glb");
            if (glb == null) {
                throw new BadRequestException("meshy.task.noModelToContinue");
            }
            return new SourceRef(null, glb, source.getId());
        }
        return new SourceRef(null, modelUrl, null);
    }

    /**
     * Task types Meshy accepts directly as an {@code input_task_id} for
     * remesh/retexture/rigging.
     */
    private boolean acceptedAsInputTask(MeshyTaskType type) {
        return switch (type) {
            case IMAGE_TO_3D, MULTI_IMAGE_TO_3D, TEXT_TO_3D_PREVIEW, TEXT_TO_3D_REFINE,
                    REMESH, RETEXTURE ->
                true;
            // FIGURE_PROTOTYPE, FIGURE_BUILD, RIG, ANIMATE -> continue via model URL.
            default -> false;
        };
    }

    private void mergeInto(MeshyTask task, MeshyTaskDto remote) {
        // Once the model has been edited in place (uploaded/base added/auto-stripped),
        // a stored asset
        // is the authoritative model and its thumbnail may be a captured one. A late or
        // duplicate
        // Meshy webhook must NOT revert those back to the original Meshy URLs.
        boolean locallyEdited = assetRepository.existsById(task.getId());
        if (remote.getStatus() != null) {
            task.setStatus(parseStatus(remote.getStatus()));
        }
        if (remote.getProgress() != null) {
            task.setProgress(remote.getProgress());
        }
        // Webhook payload URLs are attacker-influenced input (finding TRUNG: the
        // webhook is only
        // protected by a shared secret, and callers of this class later fetch these
        // URLs
        // server-side, e.g. downloadModel/fetchTexture/fetchThumbnailImage). Drop
        // anything that
        // isn't a safe public http(s) URL rather than trusting it blindly.
        Map<String, String> safeModelUrls = safeUrlMap(remote.getModelUrls());
        if (!locallyEdited && !safeModelUrls.isEmpty()) {
            task.setModelUrls(safeModelUrls);
        }
        List<MeshyTextureDto> safeTextureUrls = safeTextures(remote.getTextureUrls());
        if (!safeTextureUrls.isEmpty()) {
            task.setTextureUrls(safeTextureUrls);
        }
        String safeThumbnailUrl = safeUrl(remote.getThumbnailUrl());
        if (!locallyEdited && safeThumbnailUrl != null) {
            // Cache the preview bytes now, while the signed Meshy URL is still valid, so
            // the
            // thumbnail never breaks later when that CDN link expires. Point the task at
            // our own
            // same-origin proxy on success; otherwise keep the remote URL as a best-effort
            // fallback.
            if (task.getStatus() == MeshyTaskStatus.SUCCEEDED
                    && cacheThumbnailBytes(task.getId(), safeThumbnailUrl)) {
                task.setThumbnailUrl(selfThumbnailUrl(task.getId()));
            } else {
                task.setThumbnailUrl(safeThumbnailUrl);
            }
        }
        if (remote.getConsumedCredits() != null) {
            task.setConsumedCredits(remote.getConsumedCredits());
        }
        Map<String, String> animations = safeUrlMap(buildAnimationUrls(remote.getResult()));
        if (!animations.isEmpty()) {
            task.setAnimationUrls(animations);
        }
        if (task.getStatus() == MeshyTaskStatus.FAILED && remote.getTaskError() != null) {
            task.setErrorMessage(remote.getTaskError().getMessage());
        }
        // Track when Meshy CDN URLs will expire. Meshy retains task assets for ~14 days
        // after
        // completion. We set this only once (when first reaching SUCCEEDED) to avoid
        // drift.
        if (task.getStatus() == MeshyTaskStatus.SUCCEEDED && task.getExpiresAt() == null) {
            task.setExpiresAt(Instant.now().plus(Duration.ofDays(60)));
        }
    }

    /**
     * Null (and logs) if the URL isn't a safe public http(s) target; otherwise
     * returns it as-is.
     */
    private String safeUrl(String url) {
        if (url == null) {
            return null;
        }
        if (!SsrfGuard.isSafe(url)) {
            log.warn("Rejecting unsafe URL from Meshy webhook payload: {}", url);
            return null;
        }
        return url;
    }

    /** Filters a URL map down to entries whose value passes {@link #safeUrl}. */
    private Map<String, String> safeUrlMap(Map<String, String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Map.of();
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        urls.forEach((key, value) -> {
            String safe = safeUrl(value);
            if (safe != null) {
                filtered.put(key, safe);
            }
        });
        return filtered;
    }

    /** Same idea as {@link #safeUrlMap} but for the texture-set list shape. */
    private List<MeshyTextureDto> safeTextures(List<MeshyTextureDto> textures) {
        if (textures == null || textures.isEmpty()) {
            return List.of();
        }
        return textures.stream().map(t -> new MeshyTextureDto(
                safeUrl(t.getBaseColor()), safeUrl(t.getMetallic()),
                safeUrl(t.getNormal()), safeUrl(t.getRoughness()), safeUrl(t.getEmission()))).toList();
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

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Insert/replace the local model blob for a task. */
    private void storeAsset(UUID taskId, byte[] bytes, String format) {
        MeshyTaskAsset asset = assetRepository.findById(taskId).orElseGet(MeshyTaskAsset::new);
        String contentType = contentTypeForModel(format);
        // Drop the previous "current" object — unless it is the shared original backup
        // (backupOriginalIfMissing seeds a new row's live key with the backup's key).
        if (asset.getStorageKey() != null && !asset.getStorageKey().equals(asset.getOriginalStorageKey())) {
            objectStorage.deleteAfterCommit(asset.getStorageKey());
        }
        asset.setTaskId(taskId);
        asset.setFormat(format);
        asset.setContentType(contentType);
        asset.setStorageKey(objectStorage.put(MeshyStorageKeys.model(taskId), format, bytes, contentType));
        asset.setSize(bytes.length);
        assetRepository.save(asset);
    }

    /**
     * Snapshot {@code bytes} as the task's pristine-original backup, but only the first time —
     * a second repair must never overwrite an already-repaired mesh over the true original.
     */
    private void backupOriginalIfMissing(UUID taskId, byte[] bytes, String format) {
        MeshyTaskAsset asset = assetRepository.findById(taskId).orElse(null);
        if (asset != null && asset.getOriginalStorageKey() != null) {
            return; // already backed up — never overwrite it
        }
        if (asset == null) {
            // No local asset row yet: these pre-repair bytes ARE the original. The storeAsset()
            // call right after this one fills the row's live fields with the repaired mesh.
            asset = new MeshyTaskAsset();
            asset.setTaskId(taskId);
        }
        String contentType = contentTypeForModel(format);
        String originalKey = objectStorage.put(MeshyStorageKeys.originalModel(taskId), format, bytes, contentType);
        if (asset.getStorageKey() == null) {
            // Live fields are NOT NULL: until storeAsset() replaces them, point them at the backup.
            asset.setFormat(format);
            asset.setContentType(contentType);
            asset.setStorageKey(originalKey);
            asset.setSize(bytes.length);
        }
        asset.setOriginalFormat(format);
        asset.setOriginalContentType(contentType);
        asset.setOriginalStorageKey(originalKey);
        asset.setOriginalSize((long) bytes.length);
        assetRepository.save(asset);
    }

    /**
     * Relative same-origin proxy URL for a task's model (served by GET
     * /tasks/{id}/model).
     */
    private String selfModelUrl(UUID id) {
        return "/api/common/3d/tasks/" + id + "/model";
    }

    private String selfThumbnailUrl(UUID id) {
        return "/api/common/3d/tasks/" + id + "/thumbnail";
    }

    /** A data URI Meshy accepts as {@code model_url} for a locally-stored model. */
    private String toModelDataUri(byte[] bytes) {
        return "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String modelExtension(String filename) {
        if (filename == null) {
            throw new BadRequestException("validation.model.unsupportedType");
        }
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_MODEL_EXTS.contains(ext)) {
            throw new BadRequestException("validation.model.unsupportedType");
        }
        return ext;
    }

    private String contentTypeForModel(String fmt) {
        return switch (fmt) {
            case "glb" -> "model/gltf-binary";
            case "gltf" -> "model/gltf+json";
            case "obj" -> "text/plain";
            case "stl" -> "model/stl";
            default -> "application/octet-stream";
        };
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "model" : name;
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
    private record SourceRef(String inputTaskId, String modelUrl, UUID parentId) {
    }
}
