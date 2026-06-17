package com.innerstyle.meshy.controller;

import com.innerstyle.common.response.ApiResponse;
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
import com.innerstyle.meshy.service.MeshyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
@RequestMapping("/api/common/3d")
@RequiredArgsConstructor
public class MeshyController {

    private final MeshyTaskService meshyTaskService;

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

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get a 3D task and its current results")
    public ApiResponse<MeshyTaskResponse> getTask(@PathVariable UUID id) {
        return ApiResponse.success("meshy.task.found", meshyTaskService.getById(id));
    }

    @GetMapping("/tasks")
    @Operation(summary = "List 3D tasks (optionally filtered by status)")
    public ApiResponse<Page<MeshyTaskResponse>> listTasks(
            @RequestParam(required = false) MeshyTaskStatus status,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success("meshy.tasks.found", meshyTaskService.list(status, pageable));
    }
}
