package com.innerstyle.meshy.repository;

import java.util.UUID;

/**
 * Projection of a row whose bytes still sit in a legacy BYTEA column (pre-R2), used only by
 * the one-off backfill. {@code mapName} is set for textures only; {@code ext} is the file extension.
 */
public interface LegacyBlobRef {

    UUID getTaskId();

    String getMapName();

    String getExt();

    String getContentType();
}
