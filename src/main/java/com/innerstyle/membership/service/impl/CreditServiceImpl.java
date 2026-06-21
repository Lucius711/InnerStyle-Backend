package com.innerstyle.membership.service.impl;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.membership.entity.CreditTransaction;
import com.innerstyle.membership.entity.MembershipPlan;
import com.innerstyle.membership.entity.UserMembership;
import com.innerstyle.membership.entity.enums.CreditTransactionType;
import com.innerstyle.membership.entity.enums.MembershipStatus;
import com.innerstyle.membership.repository.CreditTransactionRepository;
import com.innerstyle.membership.repository.MembershipPlanRepository;
import com.innerstyle.membership.repository.OperationCreditRepository;
import com.innerstyle.membership.repository.UserMembershipRepository;
import com.innerstyle.membership.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Default {@link CreditService}. Credit mutations take a pessimistic lock on the membership row
 * and append an immutable ledger entry. Monthly renewal is lazy: on access, if the period has
 * elapsed, credits are reset to the plan allowance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private static final String FREE = "FREE";

    private final UserRepository userRepository;
    private final MembershipPlanRepository planRepository;
    private final OperationCreditRepository operationCreditRepository;
    private final UserMembershipRepository membershipRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Override
    @Transactional
    public UserMembership getOrCreateMembership(UUID userId) {
        return renewIfNeeded(ensureMembership(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public int creditCost(String taskType) {
        return operationCreditRepository.findByTaskTypeAndActiveTrue(taskType)
            .map(c -> c.getCreditCost())
            .orElse(0);
    }

    @Override
    @Transactional
    public void consume(UUID userId, String taskType, String referenceType, UUID referenceId) {
        int cost = creditCost(taskType);
        if (cost <= 0) {
            return; // free operation
        }
        UserMembership m = lockAndRenew(userId);
        if (m.getCreditsRemaining() < cost) {
            throw new BadRequestException("credit.insufficient");
        }
        m.setCreditsRemaining(m.getCreditsRemaining() - cost);
        membershipRepository.save(m);
        appendLedger(userId, CreditTransactionType.CONSUME, cost, m.getCreditsRemaining(),
            referenceType, referenceId, "3D " + taskType);
        log.info("Consumed {} credits from user {} for {}; remaining {}",
            cost, userId, taskType, m.getCreditsRemaining());
    }

    @Override
    @Transactional
    public void refund(UUID userId, int amount, String referenceType, UUID referenceId) {
        if (amount <= 0) {
            return;
        }
        UserMembership m = lockAndRenew(userId);
        m.setCreditsRemaining(m.getCreditsRemaining() + amount);
        membershipRepository.save(m);
        appendLedger(userId, CreditTransactionType.REFUND, amount, m.getCreditsRemaining(),
            referenceType, referenceId, "Refund for failed/cancelled job");
    }

    @Override
    @Transactional
    public void activatePlan(UUID userId, String planCode) {
        MembershipPlan plan = planRepository.findByCode(planCode)
            .orElseThrow(() -> new ResourceNotFoundException("membership.plan.notFound"));
        UserMembership m = lockAndRenew(userId);
        Instant now = Instant.now();
        m.setPlan(plan);
        m.setCreditsRemaining(plan.getMonthlyCredits());
        m.setPeriodStart(now);
        m.setPeriodEnd(oneMonthFrom(now));
        m.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(m);
        appendLedger(userId, CreditTransactionType.GRANT, plan.getMonthlyCredits(),
            m.getCreditsRemaining(), "SUBSCRIPTION", null, "Activated plan " + plan.getCode());
        log.info("Activated plan {} for user {} ({} credits)", plan.getCode(), userId,
            plan.getMonthlyCredits());
    }

    // --------------------------------------------------------------------- helpers

    private UserMembership ensureMembership(UUID userId) {
        return membershipRepository.findByUserId(userId).orElseGet(() -> createFree(userId));
    }

    private UserMembership createFree(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        MembershipPlan free = planRepository.findByCode(FREE)
            .orElseThrow(() -> new IllegalStateException("Seed plan FREE missing"));
        Instant now = Instant.now();
        UserMembership m = new UserMembership();
        m.setUser(user);
        m.setPlan(free);
        m.setCreditsRemaining(free.getMonthlyCredits());
        m.setPeriodStart(now);
        m.setPeriodEnd(oneMonthFrom(now));
        m.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(m);
        appendLedger(userId, CreditTransactionType.GRANT, free.getMonthlyCredits(),
            free.getMonthlyCredits(), "SIGNUP", null, "Free plan granted");
        return m;
    }

    private UserMembership lockAndRenew(UUID userId) {
        UserMembership m = ensureMembership(userId);
        UserMembership locked = membershipRepository.findByIdForUpdate(m.getId())
            .orElseThrow(() -> new ResourceNotFoundException("membership.notFound"));
        return renewIfNeeded(locked);
    }

    /** Lazy monthly reset: when the period has elapsed, refill to the plan allowance. */
    private UserMembership renewIfNeeded(UserMembership m) {
        Instant now = Instant.now();
        if (m.getPeriodEnd() != null && m.getPeriodEnd().isBefore(now)) {
            m.setCreditsRemaining(m.getPlan().getMonthlyCredits());
            m.setPeriodStart(now);
            m.setPeriodEnd(oneMonthFrom(now));
            m.setStatus(MembershipStatus.ACTIVE);
            membershipRepository.save(m);
            appendLedger(m.getUser().getId(), CreditTransactionType.GRANT,
                m.getPlan().getMonthlyCredits(), m.getCreditsRemaining(), "RENEWAL", null,
                "Monthly credit renewal");
        }
        return m;
    }

    private void appendLedger(UUID userId, CreditTransactionType type, int amount, int balanceAfter,
                              String refType, UUID refId, String description) {
        CreditTransaction tx = new CreditTransaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setReferenceType(refType);
        tx.setReferenceId(refId);
        tx.setDescription(description);
        creditTransactionRepository.save(tx);
    }

    private Instant oneMonthFrom(Instant start) {
        return start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    }
}
