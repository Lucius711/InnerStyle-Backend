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

    private MeshyTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        var props = new MeshyProperties("test-key", "https://api.meshy.ai", "secret",
            Duration.ofSeconds(10), Duration.ofSeconds(60),
            new MeshyProperties.Poll(true, 15000L, 25));
        service = new MeshyTaskServiceImpl(meshyClient, taskRepository, assetRepository, usdzRepository,
            thumbnailRepository, textureRepository, taskMapper, props, creditService, contentModeration,
            meshToolRunner, objectStorage);

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
}
