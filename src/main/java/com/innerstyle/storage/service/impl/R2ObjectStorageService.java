package com.innerstyle.storage.service.impl;

import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.storage.config.StorageProperties;
import com.innerstyle.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2ObjectStorageService implements ObjectStorageService {

    private static final String NOT_FOUND = "storage.object.notFound";
    private static final String UNAVAILABLE = "storage.unavailable";

    private final S3Client r2Client;
    private final StorageProperties properties;

    @Override
    public String put(String keyPrefix, String ext, byte[] data, String contentType) {
        String key = keyPrefix + "/" + UUID.randomUUID() + "." + ext;
        try {
            r2Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build(),
                RequestBody.fromBytes(data));
        } catch (SdkException e) {
            log.error("R2 upload failed for key {}: {}", key, e.getMessage());
            throw new UpstreamServiceException(UNAVAILABLE);
        }
        deleteOnRollback(key);
        return key;
    }

    @Override
    public byte[] get(String key) {
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        try {
            return r2Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .build())
                .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException(NOT_FOUND);
        } catch (SdkException e) {
            log.error("R2 download failed for key {}: {}", key, e.getMessage());
            throw new UpstreamServiceException(UNAVAILABLE);
        }
    }

    @Override
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    /** Undo an upload whose owning transaction did not commit, so no orphan is left behind. */
    private void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key);
                }
            }
        });
    }

    /** Best-effort delete: an orphaned object only costs storage, never correctness. */
    private void deleteQuietly(String key) {
        try {
            r2Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (SdkException e) {
            log.warn("R2 delete failed for key {} (orphan left behind): {}", key, e.getMessage());
        }
    }

    private String bucket() {
        return properties.r2().bucket();
    }
}
