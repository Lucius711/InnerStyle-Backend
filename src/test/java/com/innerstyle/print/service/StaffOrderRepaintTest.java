package com.innerstyle.print.service;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.meshy.dto.response.MeshyTaskResponse;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.service.PrintabilityService;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.print.service.impl.StaffOrderServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Apply/undo of a staff AI repaint swaps the order's model and can always be reversed. */
class StaffOrderRepaintTest {

    private final PrintOrderRepository orders = mock(PrintOrderRepository.class);
    private final MeshyTaskService meshy = mock(MeshyTaskService.class);
    private final StaffOrderService service = new StaffOrderServiceImpl(orders, mock(MeshyTaskRepository.class),
            meshy, mock(PrintabilityService.class));

    @Test
    void applyThenUndoRestoresOriginalModel() {
        UUID orderId = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        UUID repaintId = UUID.randomUUID();
        PrintOrder order = new PrintOrder();
        order.setSourceTaskId(original);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        MeshyTaskResponse repaint = new MeshyTaskResponse();
        repaint.setId(repaintId);
        repaint.setStatus(MeshyTaskStatus.SUCCEEDED);
        when(meshy.latestRepaint(original)).thenReturn(repaint);

        assertThat(service.applyRepaint(orderId).applied()).isTrue();
        assertThat(order.getSourceTaskId()).isEqualTo(repaintId);
        assertThat(order.getRepaintSourceTaskId()).isEqualTo(original);

        assertThat(service.undoRepaint(orderId).applied()).isFalse();
        assertThat(order.getSourceTaskId()).isEqualTo(original);
        assertThat(order.getRepaintSourceTaskId()).isNull();
    }

    @Test
    void applyRejectsUnfinishedRepaint() {
        UUID orderId = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        PrintOrder order = new PrintOrder();
        order.setSourceTaskId(original);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        MeshyTaskResponse repaint = new MeshyTaskResponse();
        repaint.setStatus(MeshyTaskStatus.IN_PROGRESS);
        when(meshy.latestRepaint(original)).thenReturn(repaint);

        assertThatThrownBy(() -> service.applyRepaint(orderId)).isInstanceOf(BadRequestException.class);
        assertThat(order.getSourceTaskId()).isEqualTo(original);
    }
}
