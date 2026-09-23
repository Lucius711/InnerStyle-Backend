package com.innerstyle.storage.service.impl;

import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.storage.config.StorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2ObjectStorageServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock private S3Client r2Client;

    private R2ObjectStorageService storage;

    @BeforeEach
    void setUp() {
        var props = new StorageProperties(
            new StorageProperties.R2("https://example.r2.cloudflarestorage.com", "id", "secret", BUCKET),
            new StorageProperties.Backfill(false, 20));
        storage = new R2ObjectStorageService(r2Client, props);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void putUploadsUnderFreshKeyWithPrefixAndExtension() {
        String key = storage.put("tasks/abc/model", "glb", new byte[] {1, 2, 3}, "model/gltf-binary");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(key).startsWith("tasks/abc/model/").endsWith(".glb");
        assertThat(captor.getValue().key()).isEqualTo(key);
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().contentLength()).isEqualTo(3L);
    }

    @Test
    void putDeletesUploadedObjectWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        String key = storage.put("tasks/abc/model", "glb", new byte[] {1}, "model/gltf-binary");
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(r2Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void putKeepsUploadedObjectWhenTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        storage.put("tasks/abc/model", "glb", new byte[] {1}, "model/gltf-binary");
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(r2Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void putWrapsSdkFailureAsUpstreamError() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage.put("p", "glb", new byte[] {1}, "model/gltf-binary"))
            .isInstanceOf(UpstreamServiceException.class)
            .hasMessage("storage.unavailable");
    }

    @Test
    void getMissingKeyThrowsNotFound() {
        when(r2Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("nope").build());

        assertThatThrownBy(() -> storage.get("tasks/x/model/y.glb"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("storage.object.notFound");
    }

    @Test
    void getNullKeyThrowsNotFoundWithoutCallingR2() {
        assertThatThrownBy(() -> storage.get(null))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(r2Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void deleteAfterCommitDefersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        storage.deleteAfterCommit("tasks/x/old.glb");
        verify(r2Client, never()).deleteObject(any(DeleteObjectRequest.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(r2Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
