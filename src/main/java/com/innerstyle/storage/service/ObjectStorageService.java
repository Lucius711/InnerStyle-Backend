package com.innerstyle.storage.service;

/**
 * Binary object storage (Cloudflare R2). Objects are immutable: every {@link #put} writes a new,
 * unique key, so a rolled-back DB transaction can never leave a row pointing at overwritten bytes.
 */
public interface ObjectStorageService {

    /**
     * Upload {@code data} under a fresh key {@code <keyPrefix>/<uuid>.<ext>} and return that key.
     * When called inside a transaction, the object is deleted again if the transaction rolls back.
     */
    String put(String keyPrefix, String ext, byte[] data, String contentType);

    /** Download an object's bytes. Throws {@code ResourceNotFoundException} if the key is null/missing. */
    byte[] get(String key);

    /** Delete an object once the current transaction commits (immediately if none). Null-safe. */
    void deleteAfterCommit(String key);
}
