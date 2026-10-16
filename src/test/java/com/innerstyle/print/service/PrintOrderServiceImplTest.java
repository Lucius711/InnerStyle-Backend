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
import com.innerstyle.print.entity.enums.PrintMaterial;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.print.service.impl.PrintOrderServiceImpl;
import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
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
        printProperties.getMaterials().put(PrintMaterial.PLA, BigDecimal.ZERO);
        printProperties.getMaterials().put(PrintMaterial.RESIN, new BigDecimal("150000"));
        service = new PrintOrderServiceImpl(printOrderRepository, meshyTaskRepository,
            userRepository, paymentService, printProperties, mock(com.innerstyle.print.service.PrintOrderEventService.class));
    }

    private CreatePrintOrderRequest request(String provider, Integer sizeCm) {
        CreatePrintOrderRequest r = new CreatePrintOrderRequest();
        r.setTaskId(taskId);
        r.setProvider(provider);
        r.setSizeCm(sizeCm);
        r.setMaterial(PrintMaterial.PLA);
        r.setRecipientName("Nguyen Van A");
        r.setRecipientEmail("a@example.com");
        r.setRecipientPhone("0901234567");
        return r;
    }

    private MeshyTask task(UUID owner, MeshyTaskStatus status) {
        MeshyTask t = new MeshyTask();
        t.setUserId(owner);
        t.setStatus(status);
        t.setModelUrls(Map.of("glb", "https://example.com/model.glb"));
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
            eq(PaymentProvider.PAYOS), eq("1.2.3.4")))
            .thenReturn(new PaymentInitResponse("IS1", "PAYOS", new BigDecimal("749000"), "https://pay", null));

        PrintOrderInitResponse res = service.placeOrder(userId, request("PAYOS", 12), "1.2.3.4");

        assertThat(res.payment().amount()).isEqualByComparingTo("749000");
        assertThat(res.payment().payUrl()).isEqualTo("https://pay");
    }

    @Test
    @DisplayName("placeOrder: task owned by another user → print.task.notOwned")
    void placeOrder_notOwned() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(UUID.randomUUID(), MeshyTaskStatus.SUCCEEDED)));

        assertThatThrownBy(() -> service.placeOrder(userId, request("PAYOS", 12), "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.task.notOwned");
        verify(paymentService, never()).createPrintPayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("placeOrder: model not SUCCEEDED → print.task.notReady")
    void placeOrder_notReady() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.PENDING)));

        assertThatThrownBy(() -> service.placeOrder(userId, request("PAYOS", 12), "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.task.notReady");
    }

    @Test
    @DisplayName("placeOrder: unknown task → meshy.task.notFound")
    void placeOrder_unknownTask() {
        when(meshyTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(userId, request("PAYOS", 12), "ip"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("meshy.task.notFound");
    }

    @Test
    @DisplayName("placeOrder: unsupported size → print.size.invalid")
    void placeOrder_badSize() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.placeOrder(userId, request("PAYOS", 99), "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.size.invalid");
    }

    @Test
    @DisplayName("placeOrder: material surcharge is added to the size price server-side")
    void placeOrder_materialSurcharge() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(printOrderRepository.save(any(PrintOrder.class))).thenAnswer(i -> i.getArgument(0));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setMaterial(PrintMaterial.RESIN);

        service.placeOrder(userId, r, "ip");

        verify(paymentService).createPrintPayment(eq(userId), any(), eq(new BigDecimal("899000")),
            eq(PaymentProvider.PAYOS), eq("ip"));
    }

    @Test
    @DisplayName("placeOrder: LED light adds its price; the card is free and keeps a trimmed message")
    void placeOrder_giftExtras() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(printOrderRepository.save(any(PrintOrder.class))).thenAnswer(i -> i.getArgument(0));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setWithLight(true);
        r.setWithCard(true);
        r.setCardMessage("  Chuc mung sinh nhat!  ");

        service.placeOrder(userId, r, "ip");

        verify(paymentService).createPrintPayment(eq(userId), any(), eq(new BigDecimal("799000")),
            eq(PaymentProvider.PAYOS), eq("ip"));
        org.mockito.ArgumentCaptor<PrintOrder> saved = org.mockito.ArgumentCaptor.forClass(PrintOrder.class);
        verify(printOrderRepository).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getCardMessage()).isEqualTo("Chuc mung sinh nhat!");
        org.assertj.core.api.Assertions.assertThat(saved.getValue().isWithLight()).isTrue();
    }

    @Test
    @DisplayName("placeOrder: a card message without the card option is dropped")
    void placeOrder_messageWithoutCard() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(printOrderRepository.save(any(PrintOrder.class))).thenAnswer(i -> i.getArgument(0));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setCardMessage("hi");

        service.placeOrder(userId, r, "ip");

        org.mockito.ArgumentCaptor<PrintOrder> saved = org.mockito.ArgumentCaptor.forClass(PrintOrder.class);
        verify(printOrderRepository).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getCardMessage()).isNull();
        verify(paymentService).createPrintPayment(eq(userId), any(), eq(new BigDecimal("749000")),
            eq(PaymentProvider.PAYOS), eq("ip"));
    }

    @Test
    @DisplayName("placeOrder: material without a configured surcharge → print.material.invalid")
    void placeOrder_badMaterial() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setMaterial(PrintMaterial.PETG);

        assertThatThrownBy(() -> service.placeOrder(userId, r, "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.material.invalid");
        verify(paymentService, never()).createPrintPayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("placeOrder: Couple Set → both figurines priced, 13% off, rounded to 1,000 VND")
    void placeOrder_coupleSet() {
        UUID pairId = UUID.randomUUID();
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(meshyTaskRepository.findById(pairId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(printOrderRepository.save(any(PrintOrder.class))).thenAnswer(i -> i.getArgument(0));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setPairTaskId(pairId);
        r.setPairSizeCm(12);

        service.placeOrder(userId, r, "ip");

        // (749,000 + 749,000) × 87% = 1,303,260 → 1,303,000
        verify(paymentService).createPrintPayment(eq(userId), any(), eq(new BigDecimal("1303000")),
            eq(PaymentProvider.PAYOS), eq("ip"));
    }

    @Test
    @DisplayName("placeOrder: Couple Set with the same model twice → print.couple.sameModel")
    void placeOrder_coupleSameModel() {
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setPairTaskId(taskId);
        r.setPairSizeCm(12);

        assertThatThrownBy(() -> service.placeOrder(userId, r, "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.couple.sameModel");
    }

    @Test
    @DisplayName("placeOrder: Couple Set with someone else's model → print.task.notOwned")
    void placeOrder_couplePairNotOwned() {
        UUID pairId = UUID.randomUUID();
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(meshyTaskRepository.findById(pairId))
            .thenReturn(Optional.of(task(UUID.randomUUID(), MeshyTaskStatus.SUCCEEDED)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setPairTaskId(pairId);
        r.setPairSizeCm(12);

        assertThatThrownBy(() -> service.placeOrder(userId, r, "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.task.notOwned");
    }

    @Test
    @DisplayName("placeOrder: Couple Set with a 2D concept (no 3D model) → print.task.noModel")
    void placeOrder_couplePairHasNoModel() {
        UUID pairId = UUID.randomUUID();
        MeshyTask concept = task(userId, MeshyTaskStatus.SUCCEEDED);
        concept.setModelUrls(null);
        when(meshyTaskRepository.findById(taskId))
            .thenReturn(Optional.of(task(userId, MeshyTaskStatus.SUCCEEDED)));
        when(meshyTaskRepository.findById(pairId)).thenReturn(Optional.of(concept));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        CreatePrintOrderRequest r = request("PAYOS", 12);
        r.setPairTaskId(pairId);
        r.setPairSizeCm(12);

        assertThatThrownBy(() -> service.placeOrder(userId, r, "ip"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("print.task.noModel");
    }
}
