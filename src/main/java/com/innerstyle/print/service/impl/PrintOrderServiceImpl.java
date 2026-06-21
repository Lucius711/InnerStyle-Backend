package com.innerstyle.print.service.impl;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default {@link PrintOrderService}. Validates ownership of a SUCCEEDED model, creates a PENDING
 * print order, and starts a direct VNPay/MoMo payment. The payment IPN/return marks it PAID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintOrderServiceImpl implements PrintOrderService {

    private final PrintOrderRepository printOrderRepository;
    private final MeshyTaskRepository meshyTaskRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    @Value("${app.print.fee:300000}")
    private BigDecimal printFee;

    @Override
    @Transactional
    public PrintOrderInitResponse placeOrder(UUID userId, UUID taskId, PaymentProvider provider,
                                             String note, String clientIp) {
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

        PrintOrder order = new PrintOrder();
        order.setUser(user);
        order.setSourceTaskId(taskId);
        order.setAmount(printFee);
        order.setStatus(PrintOrderStatus.PENDING);
        order.setNote(note);
        printOrderRepository.save(order);

        PaymentInitResponse pay = paymentService.createPrintPayment(
            userId, order.getId(), printFee, provider, clientIp);
        log.info("Print order {} placed by {} (await payment {})", order.getId(), userId, provider);
        return new PrintOrderInitResponse(order.getId(), printFee, pay.payUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrintOrderResponse> list(UUID userId, Pageable pageable) {
        return printOrderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    private PrintOrderResponse toResponse(PrintOrder o) {
        return new PrintOrderResponse(o.getId(), o.getSourceTaskId(), o.getAmount(),
            o.getCurrency(), o.getStatus().name(), o.getNote(), o.getCreatedAt());
    }
}
