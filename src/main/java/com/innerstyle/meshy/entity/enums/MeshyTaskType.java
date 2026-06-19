package com.innerstyle.meshy.entity.enums;

/**
 * The kind of MeshyAI task. Each value also knows the API path segment used to
 * retrieve it,
 * so the poller/client can route GET requests to the correct endpoint.
 */
public enum MeshyTaskType {

    IMAGE_TO_3D("v1", "image-to-3d"),
    MULTI_IMAGE_TO_3D("v1", "multi-image-to-3d"),
    TEXT_TO_3D_PREVIEW("v2", "text-to-3d"),
    TEXT_TO_3D_REFINE("v2", "text-to-3d"),
    REMESH("v1", "remesh"),
    RETEXTURE("v1", "retexture"),
    RIG("v1", "rigging"),
    ANIMATE("v1", "animations"),
    FIGURE_PROTOTYPE("creative-lab/figure/v1", "prototype"),
    FIGURE_BUILD("creative-lab/figure/v1", "build");

    private final String apiVersion;
    private final String pathSegment;

    MeshyTaskType(String apiVersion, String pathSegment) {
        this.apiVersion = apiVersion;
        this.pathSegment = pathSegment;
    }

    /** e.g. {@code /openapi/v1/image-to-3d}. */
    public String collectionPath() {
        return "/openapi/" + apiVersion + "/" + pathSegment;
    }

    /** e.g. {@code /openapi/v1/image-to-3d/{id}}. */
    public String resourcePath(String taskId) {
        return collectionPath() + "/" + taskId;
    }
}
