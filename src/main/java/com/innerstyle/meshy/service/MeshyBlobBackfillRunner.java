package com.innerstyle.meshy.service;

import com.innerstyle.meshy.repository.LegacyBlobRef;
import com.innerstyle.meshy.repository.MeshyTaskAssetRepository;
import com.innerstyle.meshy.repository.MeshyTaskTextureRepository;
import com.innerstyle.meshy.repository.MeshyTaskThumbnailRepository;
import com.innerstyle.meshy.repository.MeshyTaskUsdzRepository;
import com.innerstyle.meshy.util.MeshyStorageKeys;
import com.innerstyle.storage.config.StorageProperties;
import com.innerstyle.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * One-off, idempotent copy of legacy BYTEA blobs (pre-R2 rows) into Cloudflare R2. Each row is
 * handled in its own transaction: upload to R2, set {@code storage_key}, NULL the BYTEA. If the
 * DB update fails the upload is rolled back (deleted) by {@link ObjectStorageService}. Safe to run
 * on every startup — once nothing is left it is a handful of cheap {@code LIMIT} queries.
 * Disable with {@code app.storage.backfill.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshyBlobBackfillRunner implements ApplicationRunner {

    private final StorageProperties properties;
    private final ObjectStorageService objectStorage;
    private final TransactionTemplate transactionTemplate;
    private final MeshyTaskAssetRepository assetRepository;
    private final MeshyTaskUsdzRepository usdzRepository;
    private final MeshyTaskThumbnailRepository thumbnailRepository;
    private final MeshyTaskTextureRepository textureRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.backfill().enabled()) {
            return;
        }
        try {
            int moved = migrate("asset", assetRepository::findLegacyCurrent, ref -> {
                byte[] data = assetRepository.findLegacyCurrentData(ref.getTaskId()).getData();
                String key = objectStorage.put(MeshyStorageKeys.model(ref.getTaskId()), ref.getExt(), data,
                        ref.getContentType());
                return assetRepository.attachCurrentKey(ref.getTaskId(), key);
            });
            moved += migrate("asset-original", assetRepository::findLegacyOriginal, ref -> {
                byte[] data = assetRepository.findLegacyOriginalData(ref.getTaskId()).getData();
                String key = objectStorage.put(MeshyStorageKeys.originalModel(ref.getTaskId()), ref.getExt(),
                        data, ref.getContentType());
                return assetRepository.attachOriginalKey(ref.getTaskId(), key);
            });
            moved += migrate("usdz", usdzRepository::findLegacy, ref -> {
                byte[] data = usdzRepository.findLegacyData(ref.getTaskId()).getData();
                String key = objectStorage.put(MeshyStorageKeys.usdz(ref.getTaskId()), MeshyStorageKeys.USDZ_EXT,
                        data, MeshyStorageKeys.USDZ_CONTENT_TYPE);
                return usdzRepository.attachKey(ref.getTaskId(), key);
            });
            moved += migrate("thumbnail", thumbnailRepository::findLegacy, ref -> {
                byte[] data = thumbnailRepository.findLegacyData(ref.getTaskId()).getData();
                String key = objectStorage.put(MeshyStorageKeys.thumbnail(ref.getTaskId()),
                        MeshyStorageKeys.THUMBNAIL_EXT, data, MeshyStorageKeys.THUMBNAIL_CONTENT_TYPE);
                return thumbnailRepository.attachKey(ref.getTaskId(), key);
            });
            moved += migrate("texture", textureRepository::findLegacy, ref -> {
                byte[] data = textureRepository.findLegacyData(ref.getTaskId(), ref.getMapName()).getData();
                String key = objectStorage.put(MeshyStorageKeys.texture(ref.getTaskId(), ref.getMapName()),
                        ref.getExt(), data, ref.getContentType());
                return textureRepository.attachKey(ref.getTaskId(), ref.getMapName(), key);
            });
            if (moved > 0) {
                log.info("R2 backfill: moved {} legacy blobs out of PostgreSQL", moved);
            }
        } catch (RuntimeException e) {
            // Never block startup: rows not yet moved are retried on the next boot.
            log.error("R2 backfill aborted, will retry on next startup: {}", e.getMessage());
        }
    }

    /** Drain one legacy column batch by batch; stops when a batch makes no progress. */
    private int migrate(String label, IntFunction<List<LegacyBlobRef>> findBatch,
            ToIntFunction<LegacyBlobRef> moveOne) {
        int total = 0;
        while (true) {
            List<LegacyBlobRef> batch = findBatch.apply(properties.backfill().batchSize());
            int movedInBatch = 0;
            for (LegacyBlobRef ref : batch) {
                Integer updated = transactionTemplate.execute(status -> moveOne.applyAsInt(ref));
                movedInBatch += updated == null ? 0 : updated;
            }
            total += movedInBatch;
            if (batch.isEmpty() || movedInBatch == 0) {
                if (total > 0) {
                    log.info("R2 backfill [{}]: {} rows", label, total);
                }
                return total;
            }
        }
    }
}
