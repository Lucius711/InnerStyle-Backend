package com.innerstyle.meshy.util;

import java.util.UUID;

/** R2 key prefixes for a task's binaries: {@code tasks/<taskId>/<kind>/<uuid>.<ext>}. */
public final class MeshyStorageKeys {

    public static final String USDZ_EXT = "usdz";
    public static final String USDZ_CONTENT_TYPE = "model/vnd.usdz+zip";
    public static final String THUMBNAIL_EXT = "png";
    public static final String THUMBNAIL_CONTENT_TYPE = "image/png";

    private static final String ROOT = "tasks/";

    private MeshyStorageKeys() {
    }

    public static String model(UUID taskId) {
        return ROOT + taskId + "/model";
    }

    public static String originalModel(UUID taskId) {
        return ROOT + taskId + "/model-original";
    }

    public static String usdz(UUID taskId) {
        return ROOT + taskId + "/usdz";
    }

    public static String thumbnail(UUID taskId) {
        return ROOT + taskId + "/thumbnail";
    }

    public static String texture(UUID taskId, String mapName) {
        return ROOT + taskId + "/textures/" + mapName;
    }
}
