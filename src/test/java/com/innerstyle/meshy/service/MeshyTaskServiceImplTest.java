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
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.service.impl.MeshyTaskServiceImpl;
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
    @Mock private MeshyTaskMapper taskMapper;
    @Mock private CreditService creditService;

    private MeshyTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        var props = new MeshyProperties("test-key", "https://api.meshy.ai", "secret",
            Duration.ofSeconds(10), Duration.ofSeconds(60),
            new MeshyProperties.Poll(true, 15000L, 25));
        service = new MeshyTaskServiceImpl(meshyClient, taskRepository, taskMapper, props,
            creditService);

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
            true, 30000, "triangle", "a-pose", null, null, null);
        when(meshyClient.createImageTo3d(any(MeshyImageTo3dRequest.class))).thenReturn("meshy-123");
        when(taskRepository.save(any(MeshyTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskMapper.toResponse(any(MeshyTask.class)))
            .thenReturn(new MeshyTaskResponse(UUID.randomUUID(), "meshy-123",
                MeshyTaskType.IMAGE_TO_3D, MeshyTaskStatus.PENDING, 0, null, null,
                null, null, null, null, null, null, null, null));

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

        var remote = new MeshyTaskDto("meshy-xyz", "image-to-3d", "SUCCEEDED", 100,
            Map.of("glb", "https://assets/model.glb"), null, "https://assets/thumb.png",
            null, null, 30, null, null, null);

        service.applyRemoteState(remote);

        assertThat(task.getStatus()).isEqualTo(MeshyTaskStatus.SUCCEEDED);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getModelUrls()).containsEntry("glb", "https://assets/model.glb");
        assertThat(task.getConsumedCredits()).isEqualTo(30);
    }
}
