package com.innerstyle.wallet.service;

import com.innerstyle.wallet.entity.PaymentOrder;
import com.innerstyle.wallet.entity.enums.PaymentPurpose;
import com.innerstyle.wallet.entity.enums.PaymentStatus;
import com.innerstyle.wallet.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin revenue view over {@code dtb_payment_orders}: totals of settled payments plus a paged list.
 * Reconciling a single order against payOS goes through {@link PaymentService#confirmReturn}.
 * shortcut: concrete class with nested DTO records (no interface/impl split, no dto/response files) —
 * move into service/impl + dto/response when this grows past a read-only report.
 */
@Service
@RequiredArgsConstructor
public class RevenueService {

    private final PaymentOrderRepository paymentOrderRepository;

    public record Summary(BigDecimal subscriptionRevenue, BigDecimal printRevenue, BigDecimal totalRevenue,
                          long paidCount, long awaitingCount) {
    }

    public record PaymentRow(String orderCode, String purpose, String reference, BigDecimal amount,
                             String currency, String status, String customerEmail, String customerName,
                             String providerTxnRef, Instant createdAt, Instant paidAt) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        BigDecimal subscription = paymentOrderRepository.sumAmount(PaymentStatus.SUCCEEDED, PaymentPurpose.SUBSCRIPTION);
        BigDecimal print = paymentOrderRepository.sumAmount(PaymentStatus.SUCCEEDED, PaymentPurpose.PRINT);
        return new Summary(subscription, print, subscription.add(print),
            paymentOrderRepository.countByStatus(PaymentStatus.SUCCEEDED),
            paymentOrderRepository.countByStatus(PaymentStatus.PROCESSING));
    }

    @Transactional(readOnly = true)
    public Page<PaymentRow> list(PaymentStatus status, Pageable pageable) {
        Page<PaymentOrder> page = status == null
            ? paymentOrderRepository.findAllByOrderByCreatedAtDesc(pageable)
            : paymentOrderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(o -> new PaymentRow(o.getOrderCode(), o.getPurpose().name(), o.getReference(),
            o.getAmount(), o.getCurrency(), o.getStatus().name(),
            o.getUser().getEmail(), o.getUser().getFullName(),
            o.getProviderTxnRef(), o.getCreatedAt(), o.getPaidAt()));
    }
}
