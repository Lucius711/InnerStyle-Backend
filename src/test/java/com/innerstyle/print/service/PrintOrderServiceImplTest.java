package com.innerstyle.print.service;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.print.config.PrintProperties;
import com.innerstyle.print.dto.request.CreatePrintOrderRequest;
import com.innerstyle.print.dto.response.PrintOrderInitResponse;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.print.service.impl.PrintOrderServiceImpl;
import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PrintOrderServiceImpl}: ownership + readiness + size validation, and that
 * the charged amount is derived server-side from {@link PrintProperties}, never from the client.
 */
class PrintOrderServiceImplTest {

    private PrintOrderRepository printOrderRepository;
    private MeshyTaskRepository meshyTaskRepository;
    private UserRepository userRepository;
    private PaymentService paymentService;
    private PrintProperties printProperties;
    private PrintOrderServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        printOrderRepository = mock(PrintOrderRepository.class);
        meshyTaskRepository = mock(MeshyTaskRepository.class);
        userRepository = mock(UserRepository.class);
        paymentService = mock(PaymentService.class);
        printProperties = new PrintProperties();
        printProperties.getPrices().put(12, new BigDecimal("749000"));
        service = new PrintOrderServiceImpl(printOrderRepository, meshyTaskRepository,
            userRepository, paymentService, printProperties);
    }

    private CreatePrintOrderRequest request(String provider, Integer sizeCm) {
        CreatePrintOrderRequest r = new CreatePrintOrderRequest();
        r.setTaskId(taskId);
        r.setProvider(provider);
        r.setSizeCm(sizeCm);
        r.setRecipientName("Nguyen Van A");
        r.setRecipientEmail("a@example.com");
        r.setRecipientPhone("0901234567");
        return r;
    }

    private MeshyTask task(UUID owner, MeshyTaskStatus status) {
        MeshyTask t = new MeshyTask();
        t.setUserId(owner);
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("placeOrder: owned SUCCEEDED model → order created, server-side price, payUrl")
    void placeOrder_success() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(printOrderRepository.save(any(PrintOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentService.createPrintPayment(eq(userId), any(), eq(new BigDecimal("749000")),
            eq(PaymentProvider.VNPAY), eq("1.2.3.4")))
            .thenReturn(new PaymentInitResponse("IS1", "VNPAY", new BigDecimal("749000"), "https://pay"));

        PrintOrderInitResponse res = service.placeOrder(userId, request("VNPAY", 12), "1.2.3.4");

        assertThat(res.amount()).isEqualByComparingTo("749000");
        assertThat(res.payUrl()).isEqualTo("https://pay");
    }

    @Test
    @DisplayName("placeOrder: task owned by another user → print.task.notOwned")
    void placeOrder_notOwned() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(UUID.randomUUID(), MeshyTaskStatus.SUCCEEDED)));

        assertThatThrownBy(() -> service.placeOrder(userId, request("VNPAY", 12), "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.task.notOwned");
        verify(paymentService, never()).createPrintPayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("placeOrder: model not SUCCEEDED → print.task.notReady")
    void placeOrder_notReady() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.PENDING)));

        assertThatThrownBy(() -> service.placeOrder(userId, request("VNPAY", 12), "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.task.notReady");
    }

    @Test
    @DisplayName("placeOrder: unknown task → meshy.task.notFound")
    void placeOrder_unknownTask() {
        when(meshyTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(userId, request("VNPAY", 12), "ip"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("meshy.task.notFound");
    }

    @Test
    @DisplayName("placeOrder: unsupported size → print.size.invalid")
    void placeOrder_badSize() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.placeOrder(userId, request("VNPAY", 99), "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.size.invalid");
    }
}
