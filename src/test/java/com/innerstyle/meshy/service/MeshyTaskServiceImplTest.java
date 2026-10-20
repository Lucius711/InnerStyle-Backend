package com.innerstyle.meshy.service;

import com.innerstyle.auth.security.UserPrincipal;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.meshy.client.MeshyClient;
import com.innerstyle.meshy.client.dto.MeshyImageTo3dRequest;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.config.MeshyProperties;
import com.innerstyle.meshy.dto.request.AnimateRequest;
import com.innerstyle.meshy.dto.request.ImageTo3dRequest;
import com.innerstyle.meshy.dto.request.RigRequest;
import com.innerstyle.meshy.dto.response.MeshyTaskResponse;
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.mapper.MeshyTaskMapper;
import com.innerstyle.meshy.repository.MeshyTaskAssetRepository;
import com.innerstyle.meshy.repository.MeshyTaskUsdzRepository;
import com.innerstyle.meshy.repository.MeshyTaskThumbnailRepository;
import com.innerstyle.meshy.repository.MeshyTaskTextureRepository;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.service.impl.MeshyTaskServiceImpl;
import com.innerstyle.storage.service.ObjectStorageService;
import com.innerstyle.membership.service.CreditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeshyTaskServiceImplTest {

    @Mock private MeshyClient meshyClient;
    @Mock private MeshyTaskRepository taskRepository;
    @Mock private MeshyTaskAssetRepository assetRepository;
    @Mock private MeshyTaskUsdzRepository usdzRepository;
    @Mock private MeshyTaskThumbnailRepository thumbnailRepository;
    @Mock private MeshyTaskTextureRepository textureRepository;
    @Mock private MeshyTaskMapper taskMapper;
    @Mock private CreditService creditService;
    @Mock private ContentModeration contentModeration;
    @Mock private MeshToolRunner meshToolRunner;
    @Mock private ObjectStorageService objectStorage;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    private MeshyTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        var props = new MeshyProperties("test-key", "https://api.meshy.ai", "secret",
            Duration.ofSeconds(10), Duration.ofSeconds(60),
            new MeshyProperties.Poll(true, 15000L, 25));
        service = new MeshyTaskServiceImpl(meshyClient, taskRepository, assetRepository, usdzRepository,
            thumbnailRepository, textureRepository, taskMapper, props, creditService, contentModeration,
            meshToolRunner, objectStorage, events);

        // Authenticate a user so billing (beginBilling -> currentUserIdOrThrow) can run.
        var principal = UserPrincipal.fromClaims(UUID.randomUUID(), "tester@example.com",
            List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        // Default: operations are free in tests (no credits consumed) unless a test overrides this.
        lenient().when(creditService.creditCost(anyString())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createImageTo3dSubmitsAndPersistsPendingTask() {
        var request = new ImageTo3dRequest("https://example.com/p.png", "latest", true, true,
            true, 30000, "triangle", "a-pose", null, null, null, null, null);
        when(meshyClient.createImageTo3d(any(MeshyImageTo3dRequest.class))).thenReturn("meshy-123");
        when(taskRepository.save(any(MeshyTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskMapper.toResponse(any(MeshyTask.class)))
            .thenReturn(new MeshyTaskResponse(UUID.randomUUID(), "meshy-123",
                MeshyTaskType.IMAGE_TO_3D, MeshyTaskStatus.PENDING, 0, null, null,
                null, null, null, null, null, null, null, null, null, null));

        MeshyTaskResponse result = service.createImageTo3d(request);

        assertThat(result.getMeshyTaskId()).isEqualTo("meshy-123");
        ArgumentCaptor<MeshyTask> captor = ArgumentCaptor.forClass(MeshyTask.class);
        org.mockito.Mockito.verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getTaskType()).isEqualTo(MeshyTaskType.IMAGE_TO_3D);
        assertThat(captor.getValue().getStatus()).isEqualTo(MeshyTaskStatus.PENDING);
        assertThat(captor.getValue().getMeshyTaskId()).isEqualTo("meshy-123");
    }

    @Test
    void rigReusesExistingRigOfUnchangedModelWithoutCharging() {
        UUID sourceId = UUID.randomUUID();
        MeshyTask source = new MeshyTask();
        source.setId(sourceId);
        source.setMeshyTaskId("meshy-src");
        source.setTaskType(MeshyTaskType.IMAGE_TO_3D);
        source.setStatus(MeshyTaskStatus.SUCCEEDED);
        source.setUpdatedAt(java.time.Instant.parse("2026-09-01T00:00:00Z"));
        MeshyTask rig = new MeshyTask();
        rig.setTaskType(MeshyTaskType.RIG);
        rig.setStatus(MeshyTaskStatus.SUCCEEDED);
        rig.setCreatedAt(java.time.Instant.parse("2026-09-02T00:00:00Z"));
        MeshyTaskResponse reused = new MeshyTaskResponse(UUID.randomUUID(), "meshy-rig",
            MeshyTaskType.RIG, MeshyTaskStatus.SUCCEEDED, 100, sourceId, null,
            null, null, null, null, null, null, null, null, null, null);
        when(taskRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(assetRepository.findById(sourceId)).thenReturn(Optional.empty());
        when(taskRepository.findFirstByParentIdAndTaskTypeAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            any(), any(), any())).thenReturn(Optional.of(rig));
        when(taskMapper.toResponse(rig)).thenReturn(reused);

        MeshyTaskResponse result = service.rig(new RigRequest(sourceId, null, 1.7));

        assertThat(result).isSameAs(reused);
        org.mockito.Mockito.verifyNoInteractions(meshyClient);
        org.mockito.Mockito.verify(taskRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void animateRejectsRigTaskThatIsNotSucceeded() {
        UUID rigId = UUID.randomUUID();
        MeshyTask rig = new MeshyTask();
        rig.setId(rigId);
        rig.setTaskType(MeshyTaskType.RIG);
        rig.setStatus(MeshyTaskStatus.IN_PROGRESS);
        when(taskRepository.findById(rigId)).thenReturn(Optional.of(rig));

        var request = new AnimateRequest(rigId, 92, null, null);

        assertThatThrownBy(() -> service.animate(request))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("meshy.task.notSucceeded");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id, UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("meshy.task.notFound");
    }

    @Test
    void freePlanFigurineBuildUsesNoExtraGenerationAndOnlyOncePerPrototype() {
        var freePlan = new com.innerstyle.membership.entity.MembershipPlan();
        freePlan.setPrice(java.math.BigDecimal.ZERO);
        var membership = new com.innerstyle.membership.entity.UserMembership();
        membership.setPlan(freePlan);
        when(creditService.getOrCreateMembership(any())).thenReturn(membership);
        lenient().when(creditService.creditCost("FIGURE_BUILD")).thenReturn(30);

        UUID protoId = UUID.randomUUID();
        MeshyTask proto = new MeshyTask();
        proto.setId(protoId);
        proto.setMeshyTaskId("proto-1");
        proto.setTaskType(MeshyTaskType.FIGURE_PROTOTYPE);
        proto.setStatus(MeshyTaskStatus.SUCCEEDED);
        when(taskRepository.findById(protoId)).thenReturn(Optional.of(proto));
        when(meshyClient.createFigureBuild("proto-1")).thenReturn("build-1");
        // 1st call: taken? no; failed before? no.  2nd call: taken? yes.
        when(taskRepository.existsByParentIdAndTaskTypeAndStatusIn(any(), any(), any()))
            .thenReturn(false, false, true);
        var request = new com.innerstyle.meshy.dto.request.FigurineBuildRequest();
        request.setSourceTaskId(protoId);

        service.buildFigurine(request); // the prototype already took the FREE use
        verify(creditService, org.mockito.Mockito.never()).consume(any(), anyString(), anyString(), any());

        assertThatThrownBy(() -> service.buildFigurine(request))
            .isInstanceOf(com.innerstyle.common.exception.BadRequestException.class)
            .hasMessage("meshy.figurine.alreadyBuilt");
    }

    @Test
    void failedFigurineBuildGivesTheGenerationBack() {
        UUID userId = UUID.randomUUID();
        MeshyTask build = new MeshyTask();
        build.setId(UUID.randomUUID());
        build.setUserId(userId);
        build.setMeshyTaskId("build-9");
        build.setTaskType(MeshyTaskType.FIGURE_BUILD);
        build.setStatus(MeshyTaskStatus.IN_PROGRESS);
        when(taskRepository.findByMeshyTaskId("build-9")).thenReturn(Optional.of(build));
        lenient().when(taskRepository.save(any(MeshyTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(creditService.creditCost("FIGURE_BUILD")).thenReturn(30);

        service.applyRemoteState(new MeshyTaskDto("build-9", "build", "FAILED", 0,
            null, null, null, null, null, null, null, null, null));

        // FREE: FreeQuotaCreditService turns this into one use returned; paid: 30 credits back
        verify(creditService).refund(userId, 30, "MESHY_TASK", build.getId());
    }

    @Test
    void applyRemoteStateMergesModelUrlsAndStatus() {
        MeshyTask task = new MeshyTask();
        task.setMeshyTaskId("meshy-xyz");
        task.setTaskType(MeshyTaskType.IMAGE_TO_3D);
        task.setStatus(MeshyTaskStatus.IN_PROGRESS);
        when(taskRepository.findByMeshyTaskId("meshy-xyz")).thenReturn(Optional.of(task));
        lenient().when(taskRepository.save(any(MeshyTask.class))).thenAnswer(inv -> inv.getArgument(0));

        // A real public host is required here (not e.g. "https://assets/model.glb"): mergeInto
        // now runs webhook-payload URLs through the SsrfGuard used elsewhere in the codebase
        // (ImageProxyController), which resolves the hostname and rejects anything that isn't a
        // public http(s) address.
        var remote = new MeshyTaskDto("meshy-xyz", "image-to-3d", "SUCCEEDED", 100,
            Map.of("glb", "https://example.com/model.glb"), null, "https://example.com/thumb.png",
            null, null, 30, null, null, null);

        service.applyRemoteState(remote);

        assertThat(task.getStatus()).isEqualTo(MeshyTaskStatus.SUCCEEDED);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getModelUrls()).containsEntry("glb", "https://example.com/model.glb");
        assertThat(task.getConsumedCredits()).isEqualTo(30);
        // first SUCCEEDED -> one event (model-ready email); no inline R2 download any more
        verify(events).publishEvent(any(com.innerstyle.meshy.event.MeshyTaskSucceededEvent.class));
        verifyNoInteractions(objectStorage);

        service.applyRemoteState(remote); // duplicate webhook/poll delivery
        verify(events).publishEvent(any(com.innerstyle.meshy.event.MeshyTaskSucceededEvent.class));
    }

    @Test
    void fetchTextureServesSecondSetFromR2WithoutTouchingCdn() {
        UUID id = UUID.randomUUID();
        MeshyTask task = new MeshyTask();
        task.setId(id);
        var set1 = new com.innerstyle.meshy.client.dto.MeshyTextureDto("https://cdn.invalid/a/base.png?sig=1", null, null, null, null);
        var set2 = new com.innerstyle.meshy.client.dto.MeshyTextureDto("https://cdn.invalid/b/base.png?sig=expired", null, null, null, null);
        task.setTextureUrls(List.of(set1, set2));
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        var cached = new com.innerstyle.meshy.entity.MeshyTaskTexture();
        cached.setTaskId(id);
        cached.setMapName("s2_base_color");
        cached.setSourceKey("https://cdn.invalid/b/base.png");
        cached.setContentType("image/png");
        cached.setExt("png");
        cached.setStorageKey("tasks/" + id + "/tex/s2.png");
        when(textureRepository.findById(new com.innerstyle.meshy.entity.MeshyTaskTextureId(id, "s2_base_color")))
            .thenReturn(Optional.of(cached));
        when(objectStorage.get("tasks/" + id + "/tex/s2.png")).thenReturn(new byte[] {7});

        var result = service.fetchTexture(id, "base_color", 2);

        assertThat(result.bytes()).containsExactly(7);
        assertThat(result.filename()).isEqualTo("texture.png");
    }

    @Test
    void fetchAnimationRejectsNameMeshyDidNotReturn() {
        UUID id = UUID.randomUUID();
        MeshyTask task = new MeshyTask();
        task.setId(id);
        task.setAnimationUrls(Map.of("walkingGlbUrl", "https://cdn.invalid/walk.glb"));
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.fetchAnimation(id, "../../etc"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fetchModelServesCachedUsdz() {
        UUID id = UUID.randomUUID();
        MeshyTask task = new MeshyTask();
        task.setId(id);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        var cached = new com.innerstyle.meshy.entity.MeshyTaskUsdz();
        cached.setTaskId(id);
        cached.setStorageKey("tasks/" + id + "/usdz/a.usdz");
        cached.setSize(3);
        when(usdzRepository.findById(id)).thenReturn(Optional.of(cached));
        when(objectStorage.get("tasks/" + id + "/usdz/a.usdz")).thenReturn(new byte[] {1, 2, 3});

        var result = service.fetchModel(id, "usdz");

        assertThat(result.contentType()).isEqualTo("model/vnd.usdz+zip");
        assertThat(result.filename()).isEqualTo("model.usdz");
        assertThat(result.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void fetchModelThrowsWhenUsdzNeitherCachedNorHosted() {
        UUID id = UUID.randomUUID();
        MeshyTask task = new MeshyTask();
        task.setId(id);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));
        when(usdzRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchModel(id, "usdz"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("meshy.task.notFound");
    }

    @Test
    void storeUsdzPersistsBytesForExistingTask() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        MeshyTask task = new MeshyTask();
        task.setId(id);
        task.setUserId(owner);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));
        when(usdzRepository.findById(id)).thenReturn(Optional.empty());
        when(objectStorage.put("tasks/" + id + "/usdz", "usdz", new byte[] {9, 8, 7, 6}, "model/vnd.usdz+zip"))
            .thenReturn("tasks/" + id + "/usdz/new.usdz");

        service.storeUsdz(id, owner, new byte[] {9, 8, 7, 6});

        ArgumentCaptor<com.innerstyle.meshy.entity.MeshyTaskUsdz> captor =
            ArgumentCaptor.forClass(com.innerstyle.meshy.entity.MeshyTaskUsdz.class);
        org.mockito.Mockito.verify(usdzRepository).save(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(id);
        assertThat(captor.getValue().getSize()).isEqualTo(4);
        assertThat(captor.getValue().getStorageKey()).isEqualTo("tasks/" + id + "/usdz/new.usdz");
    }

    @Test
    void storeUsdzRejectsEmptyBody() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.storeUsdz(id, UUID.randomUUID(), new byte[0]))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("meshy.usdz.empty");
    }

    @Test
    void storeUsdzRejectsNonOwner() {
        UUID id = UUID.randomUUID();
        MeshyTask task = new MeshyTask();
        task.setId(id);
        task.setUserId(UUID.randomUUID());
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.storeUsdz(id, UUID.randomUUID(), new byte[] {1, 2}))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("meshy.task.notFound");
    }

    @Test
    void backfillToR2HidesPurgedTaskWithNoModelInR2() {
        MeshyTask task = new MeshyTask();
        task.setId(UUID.randomUUID());
        task.setMeshyTaskId("meshy-gone");
        task.setTaskType(MeshyTaskType.MULTI_IMAGE_TO_3D);
        task.setStatus(MeshyTaskStatus.SUCCEEDED);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(meshyClient.getTask(MeshyTaskType.MULTI_IMAGE_TO_3D, "meshy-gone"))
            .thenThrow(new ResourceNotFoundException("meshy.task.purgedByMeshy"));

        assertThat(service.backfillToR2(task.getId())).isEqualTo(MeshyTaskService.R2BackfillResult.PURGED);
        assertThat(task.getStatus()).isEqualTo(MeshyTaskStatus.EXPIRED); // hidden from the library
    }
}
