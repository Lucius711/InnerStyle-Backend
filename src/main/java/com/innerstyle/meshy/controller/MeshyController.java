package com.innerstyle.meshy.controller;

import com.innerstyle.common.response.ApiResponse;
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
import com.innerstyle.meshy.dto.response.PrintabilityResponse;
import com.innerstyle.meshy.dto.response.RepairResponse;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.ModelOrigin;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.service.PrintabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.innerstyle.auth.security.UserPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * 2D image / text to animated 3D model endpoints (MeshyAI pipeline):
 * image-to-3D, text-to-3D (+ color refine), remesh/optimization,
 * retexture/color,
 * rigging, and animation. All creation endpoints return immediately with a
 * PENDING task;
 * the result is filled in asynchronously via webhook (primary) and polling
 * (fallback).
 */
@Tag(name = "Common - 3D Generation (MeshyAI)")
@RestController
@RequestMapping("/common/3d")
@RequiredArgsConstructor
public class MeshyController {

    private final MeshyTaskService meshyTaskService;
    private final PrintabilityService printabilityService;

    @GetMapping("/tasks/{id}/printability")
    @Operation(summary = "Analyse a model's 3D-print readiness (watertight / volume / holes / non-manifold)")
    public ApiResponse<PrintabilityResponse> printability(@PathVariable UUID id) {
        return ApiResponse.success("meshy.printability", printabilityService.analyze(id));
    }

    @PostMapping("/tasks/{id}/repair")
    @Operation(summary = "Auto-repair the model into a watertight, printable mesh and SAVE it in "
            + "place (no file download — the existing download button serves the repaired model). "
            + "Owner-only. Returns the before/after printability reports + updated task.")
    public ApiResponse<RepairResponse> repair(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.task.updated",
                meshyTaskService.repairInPlace(id, principal.getId()));
    }

    @PostMapping("/image-to-3d")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert a 2D image into a 3D model (geometry + color, optional optimization & pose)")
    public ApiResponse<MeshyTaskResponse> imageTo3d(@Valid @RequestBody ImageTo3dRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.createImageTo3d(request));
    }

    @PostMapping(value = "/image-to-3d/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert an uploaded image file (from your computer) into a 3D model")
    public ApiResponse<MeshyTaskResponse> imageTo3dUpload(
            @RequestPart("file") MultipartFile file,
            @Valid @ModelAttribute ImageUploadOptions options) {
        return ApiResponse.success("meshy.task.created",
                meshyTaskService.createImageTo3dFromUpload(file, options));
    }

    @PostMapping("/multi-image-to-3d")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert multiple reference images of one subject into a single 3D model")
    public ApiResponse<MeshyTaskResponse> multiImageTo3d(@Valid @RequestBody MultiImageTo3dRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.createMultiImageTo3d(request));
    }

    @PostMapping(value = "/multi-image-to-3d/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convert multiple uploaded image files (from your computer) into one 3D model")
    public ApiResponse<MeshyTaskResponse> multiImageUpload(
            @RequestPart("files") List<MultipartFile> files,
            @Valid @ModelAttribute ImageUploadOptions options) {
        return ApiResponse.success("meshy.task.created",
                meshyTaskService.createMultiImageTo3dFromUpload(files, options));
    }

    @PostMapping(value = "/import/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import an existing 3D file (.glb/.gltf/.obj/.fbx/.stl) from your computer "
            + "into your library, then run the pipeline (custom base, printability, export, or "
            + "Meshy remesh/retexture/rig) on it.")
    public ApiResponse<MeshyTaskResponse> importModel(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.task.created",
                meshyTaskService.importModel(file, principal.getId()));
    }

    @PostMapping("/tasks/{id}/base")
    @Operation(summary = "Add (or replace) a custom base/stand (cylinder/square/hexagon) under the "
            + "model and persist it in place — preview/export then include the base. Re-posting "
            + "replaces any existing base rather than stacking a new one.")
    public ApiResponse<MeshyTaskResponse> addBase(@PathVariable UUID id,
            @Valid @RequestBody BaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.task.updated",
                meshyTaskService.addBase(id, principal.getId(), request));
    }

    @DeleteMapping("/tasks/{id}/base")
    @Operation(summary = "Remove the base previously baked into the model and persist the "
            + "base-less model in place. Owner-only.")
    public ApiResponse<MeshyTaskResponse> removeBase(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.task.updated",
                meshyTaskService.removeBase(id, principal.getId()));
    }

    @PostMapping("/text-to-3d")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a 3D mesh from a text prompt (preview stage)")
    public ApiResponse<MeshyTaskResponse> textTo3d(@Valid @RequestBody TextTo3dRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.createTextTo3dPreview(request));
    }

    @PostMapping("/refine")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply color/texture to a completed text-to-3D preview")
    public ApiResponse<MeshyTaskResponse> refine(@Valid @RequestBody RefineRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.refine(request));
    }

    @PostMapping("/remesh")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Optimize a model (topology / polycount / formats)")
    public ApiResponse<MeshyTaskResponse> remesh(@Valid @RequestBody RemeshRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.remesh(request));
    }

    @PostMapping("/retexture")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Re-color / re-texture a model from a text or image style")
    public ApiResponse<MeshyTaskResponse> retexture(@Valid @RequestBody RetextureRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.retexture(request));
    }

    @PostMapping("/rig")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Auto-rig a humanoid model (skeleton + default walk/run animations)")
    public ApiResponse<MeshyTaskResponse> rig(@Valid @RequestBody RigRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.rig(request));
    }

    @PostMapping("/animate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply an animation action to a rigged character")
    public ApiResponse<MeshyTaskResponse> animate(@Valid @RequestBody AnimateRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.animate(request));
    }

    @PostMapping("/figurine")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Chibi figurine (stage 1): stylize a photo into a chibi concept image. "
            + "Optional `texturePrompt` (texture/color description) is stored and applied via "
            + "/retexture after the figure is built — Meshy's figure stages don't take a prompt.")
    public ApiResponse<MeshyTaskResponse> figurinePrototype(@Valid @RequestBody FigurineRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.createFigurinePrototype(request));
    }

    @PostMapping(value = "/figurine/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Chibi figurine (stage 1) from an uploaded image file (+ optional texture "
            + "description, applied later via /retexture)")
    public ApiResponse<MeshyTaskResponse> figurinePrototypeUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "texturePrompt", required = false) String texturePrompt) {
        return ApiResponse.success("meshy.task.created",
                meshyTaskService.createFigurinePrototypeFromUpload(file, texturePrompt));
    }

    @PostMapping("/figurine/build")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Chibi figurine (stage 2): build the textured 3D figure from a prototype. "
            + "Once SUCCEEDED, continue the same pipeline as image-to-3D by passing this task id as "
            + "`sourceTaskId` to /remesh (optimize), /retexture (re-color), or /rig -> /animate.")
    public ApiResponse<MeshyTaskResponse> figurineBuild(@Valid @RequestBody FigurineBuildRequest request) {
        return ApiResponse.success("meshy.task.created", meshyTaskService.buildFigurine(request));
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get one of MY 3D tasks and its current results")
    public ApiResponse<MeshyTaskResponse> getTask(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.task.found",
                meshyTaskService.getById(id, principal.getId()));
    }

    @GetMapping("/tasks/{id}/model")
    @Operation(summary = "Stream a task's model file (server-side proxy so browsers avoid the "
            + "CDN's missing CORS headers)")
    public ResponseEntity<byte[]> getTaskModel(@PathVariable UUID id,
            @RequestParam(required = false) String format) {
        MeshyTaskService.ModelData model = meshyTaskService.fetchModel(id, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, model.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + model.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(model.bytes());
    }

    @PutMapping(value = "/tasks/{id}/thumbnail", consumes = MediaType.IMAGE_PNG_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Replace a task's preview thumbnail with a freshly captured PNG of the "
            + "edited model. Meshy never regenerates its thumbnail after an in-place edit (e.g. a "
            + "custom base), so the editor captures the model and uploads it here. Owner only.")
    public void uploadTaskThumbnail(@PathVariable UUID id, @RequestBody byte[] png,
            @AuthenticationPrincipal UserPrincipal principal) {
        meshyTaskService.storeThumbnail(id, principal.getId(), png);
    }

    @GetMapping("/tasks/{id}/thumbnail")
    @Operation(summary = "Stream a task's captured preview image (PNG) same-origin so an <img> tag "
            + "can load it. Public, like the model proxy.")
    public ResponseEntity<byte[]> getTaskThumbnail(@PathVariable UUID id) {
        byte[] data = meshyTaskService.fetchThumbnailImage(id);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(data);
    }

    @PutMapping(value = "/tasks/{id}/usdz", consumes = "model/vnd.usdz+zip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cache the browser-built iOS AR Quick Look (USDZ) file for MY task. iOS "
            + "Quick Look cannot launch from an in-browser blob: URL, so the frontend uploads the "
            + "USDZ bytes here and then loads them via GET /tasks/{id}/model?format=usdz. Owner only "
            + "(the write requires auth + ownership); the subsequent GET stays public for Quick Look.")
    public void cacheTaskUsdz(@PathVariable UUID id, @RequestBody byte[] usdz,
            @AuthenticationPrincipal UserPrincipal principal) {
        meshyTaskService.storeUsdz(id, principal.getId(), usdz);
    }

    @PutMapping(value = "/tasks/{id}/model", consumes = "model/gltf-binary")
    @Operation(summary = "Replace MY task's model with the edited mesh exported in-browser (material "
            + "and transform edits baked via three.js GLTFExporter). Stored in place as the "
            + "authoritative GLB so preview / export / orders reflect the edits. Owner only.")
    public ApiResponse<MeshyTaskResponse> replaceTaskModel(@PathVariable UUID id,
            @RequestBody byte[] glb, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.task.updated",
                meshyTaskService.replaceModel(id, principal.getId(), glb));
    }

    @GetMapping("/tasks/{id}/texture")
    @Operation(summary = "Stream a task's texture map same-origin (so the in-browser viewer can apply "
            + "it; the Meshy CDN lacks CORS headers). Defaults to the base color map.")
    public ResponseEntity<byte[]> getTaskTexture(@PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "base_color") String map) {
        MeshyTaskService.ModelData texture = meshyTaskService.fetchTexture(id, map);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, texture.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(texture.bytes());
    }

    @GetMapping("/tasks/{id}/model/export")
    @Operation(summary = "Download MY model as a ZIP (model file + texture maps), optionally resized "
            + "to a physical height (mm) with a bottom/centre origin. Resizing is a premium feature "
            + "and only applies to printable formats (stl, obj). The model is streamed from Meshy "
            + "into the zip without buffering it server-side.")
    public ResponseEntity<StreamingResponseBody> exportTaskModel(@PathVariable UUID id,
            @RequestParam String format,
            @RequestParam(required = false) Double heightMm,
            @RequestParam(required = false, defaultValue = "BOTTOM") ModelOrigin origin,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Validate + (optionally) resize in the request thread so errors map to proper codes.
        MeshyTaskService.ExportPrep prep =
            meshyTaskService.prepareUserExport(id, principal.getId(), format, heightMm, origin);
        StreamingResponseBody body = out -> meshyTaskService.writeZip(prep, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"innerstyle-model-" + id.toString().substring(0, 8) + ".zip\"")
                .body(body);
    }

    @GetMapping("/tasks")
    @Operation(summary = "List MY 3D tasks (private library, optionally filtered by status)")
    public ApiResponse<Page<MeshyTaskResponse>> listTasks(
            @RequestParam(required = false) MeshyTaskStatus status,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("meshy.tasks.found",
                meshyTaskService.list(principal.getId(), status, pageable));
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "Delete one of MY 3D tasks (removes it from my private library)")
    public ApiResponse<Void> deleteTask(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        meshyTaskService.delete(id, principal.getId());
        return ApiResponse.success("meshy.task.deleted");
    }
}
