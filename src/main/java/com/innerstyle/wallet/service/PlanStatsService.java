package com.innerstyle.wallet.service;

import com.innerstyle.membership.entity.MembershipPlan;
import com.innerstyle.membership.repository.MembershipPlanRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin view of paid membership plans over {@code dtb_payment_orders}: how many of each plan
 * (PRO / MAX …) were bought and the revenue per plan. Only SUCCEEDED + SUBSCRIPTION orders count;
 * {@code reference} holds the plan code.
 * shortcut: concrete class with nested DTO records, same as RevenueService — split when it grows.
 */
@Service
@RequiredArgsConstructor
public class PlanStatsService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    public record PlanRow(String code, String name, BigDecimal currentPrice, long paidCount,
                          BigDecimal revenue, Instant lastPaidAt) {
    }

    public record Summary(long totalPaid, BigDecimal totalRevenue, long buyers, List<PlanRow> plans) {
    }

    public record PurchaseRow(String orderCode, String planCode, String planName, BigDecimal amount,
                              String customerEmail, String customerName, Instant paidAt) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Map<String, MembershipPlan> plans = plansByCode();
        Map<String, PaymentOrderRepository.PlanPurchaseCount> counts = new HashMap<>();
        paymentOrderRepository.countByReference(PaymentStatus.SUCCEEDED, PaymentPurpose.SUBSCRIPTION)
            .forEach(c -> counts.put(c.getPlanCode(), c));

        List<PlanRow> rows = new ArrayList<>();
        // Every paid plan appears (even with 0 purchases), in the plans' display order.
        plans.values().stream()
            .filter(p -> !p.isFree())
            .sorted(Comparator.comparingInt(MembershipPlan::getSortOrder))
            .forEach(p -> rows.add(toRow(p.getCode(), p, counts.remove(p.getCode()))));
        // Leftover codes (renamed/removed plans) still show up so totals add up.
        counts.forEach((code, c) -> rows.add(toRow(code, null, c)));

        long total = rows.stream().mapToLong(PlanRow::paidCount).sum();
        BigDecimal revenue = rows.stream().map(PlanRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        long buyers = paymentOrderRepository.countDistinctBuyers(PaymentStatus.SUCCEEDED, PaymentPurpose.SUBSCRIPTION);
        return new Summary(total, revenue, buyers, rows);
    }

    /** Paid plan purchases, newest first, optionally for one plan code. */
    @Transactional(readOnly = true)
    public Page<PurchaseRow> purchases(String planCode, Pageable pageable) {
        Page<PaymentOrder> page = planCode == null || planCode.isBlank()
            ? paymentOrderRepository.findByStatusAndPurposeOrderByCreatedAtDesc(
                PaymentStatus.SUCCEEDED, PaymentPurpose.SUBSCRIPTION, pageable)
            : paymentOrderRepository.findByStatusAndPurposeAndReferenceOrderByCreatedAtDesc(
                PaymentStatus.SUCCEEDED, PaymentPurpose.SUBSCRIPTION, planCode, pageable);
        Map<String, MembershipPlan> plans = plansByCode();
        return page.map(o -> {
            MembershipPlan plan = plans.get(o.getReference());
            return new PurchaseRow(o.getOrderCode(), o.getReference(),
                plan == null ? o.getReference() : plan.getName(), o.getAmount(),
                o.getUser().getEmail(), o.getUser().getFullName(),
                o.getPaidAt() != null ? o.getPaidAt() : o.getCreatedAt());
        });
    }

    private Map<String, MembershipPlan> plansByCode() {
        Map<String, MembershipPlan> m = new HashMap<>();
        membershipPlanRepository.findAll().forEach(p -> m.put(p.getCode(), p));
        return m;
    }

    private static PlanRow toRow(String code, MembershipPlan plan, PaymentOrderRepository.PlanPurchaseCount c) {
        return new PlanRow(code,
            plan == null ? code : plan.getName(),
            plan == null ? null : plan.getPrice(),
            c == null || c.getTotal() == null ? 0 : c.getTotal(),
            c == null || c.getRevenue() == null ? BigDecimal.ZERO : c.getRevenue(),
            c == null ? null : c.getLastPaidAt());
    }
}
