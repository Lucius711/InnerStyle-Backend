package com.innerstyle.print.service.impl;

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
import com.innerstyle.print.dto.response.PrintOrderResponse;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.print.service.PrintOrderService;
import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default {@link PrintOrderService}. Validates ownership of a SUCCEEDED model, creates a PENDING
 * print order, and starts a direct payOS payment. The payment webhook/return marks it PAID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintOrderServiceImpl implements PrintOrderService {

    private final PrintOrderRepository printOrderRepository;
    private final MeshyTaskRepository meshyTaskRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final PrintProperties printProperties;

    @Override
    @Transactional
    public PrintOrderInitResponse placeOrder(UUID userId, CreatePrintOrderRequest request, String clientIp) {
        UUID taskId = request.getTaskId();
        PaymentProvider provider = PaymentProvider.valueOf(request.getProvider());

        MeshyTask task = meshyTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("meshy.task.notFound"));
        if (task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new BadRequestException("print.task.notOwned");
        }
        if (task.getStatus() != MeshyTaskStatus.SUCCEEDED) {
            throw new BadRequestException("print.task.notReady");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));

        Integer sizeCm = request.getSizeCm();
        if (!printProperties.supports(sizeCm)) {
            throw new BadRequestException("print.size.invalid");
        }
        BigDecimal amount = printProperties.priceFor(sizeCm);

        PrintOrder order = new PrintOrder();
        order.setUser(user);
        order.setSourceTaskId(taskId);
        order.setSizeCm(sizeCm);
        order.setAmount(amount);
        order.setStatus(PrintOrderStatus.PENDING);
        order.setNote(request.getNote());
        order.setRecipientName(request.getRecipientName());
        order.setRecipientEmail(request.getRecipientEmail());
        order.setRecipientPhone(request.getRecipientPhone());
        order.setProvinceCode(request.getProvinceCode());
        order.setProvinceName(request.getProvinceName());
        order.setWardCode(request.getWardCode());
        order.setWardName(request.getWardName());
        order.setAddressDetail(request.getAddressDetail());
        order.setLatitude(request.getLatitude());
        order.setLongitude(request.getLongitude());
        printOrderRepository.save(order);

        PaymentInitResponse pay = paymentService.createPrintPayment(
            userId, order.getId(), amount, provider, clientIp);
        log.info("Print order {} placed by {} ({}cm, await payment {})",
            order.getId(), userId, sizeCm, provider);
        return new PrintOrderInitResponse(order.getId(), pay);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrintOrderResponse> list(UUID userId, Pageable pageable) {
        return printOrderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    @Override
    @Transactional
    public PrintOrderInitResponse resumePayment(UUID userId, UUID orderId, String clientIp) {
        PrintOrder order = printOrderRepository.findById(orderId)
            .filter(o -> o.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("print.order.notFound"));
        if (order.getStatus() != PrintOrderStatus.PENDING) {
            throw new BadRequestException("print.order.notPending");
        }
        PaymentInitResponse pay = paymentService.resumePrintPayment(
            userId, order.getId(), order.getAmount(), clientIp);
        return new PrintOrderInitResponse(order.getId(), pay);
    }

    private PrintOrderResponse toResponse(PrintOrder o) {
        return new PrintOrderResponse(o.getId(), o.getSourceTaskId(), o.getSizeCm(), o.getAmount(),
            o.getCurrency(), o.getStatus().name(), o.getNote(),
            o.getRecipientName(), o.getRecipientPhone(),
            o.getProvinceName(), o.getWardName(), o.getAddressDetail(),
            o.getCreatedAt());
    }
}
