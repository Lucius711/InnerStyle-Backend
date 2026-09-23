package com.innerstyle.meshy.repository;

/** Projection of a legacy BYTEA column's bytes (pre-R2), used only by the one-off backfill. */
public interface LegacyBlobData {

    byte[] getData();
}
