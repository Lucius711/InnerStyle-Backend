package com.innerstyle.membership.service;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.membership.entity.UserMembership;
import com.innerstyle.membership.repository.UserMembershipRepository;
import com.innerstyle.membership.service.impl.CreditServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The {@link CreditService} everyone gets injected: FREE members spend one of {@value #FREE_USES}
 * lifetime uses per operation (any service), paid members spend credits via {@link CreditServiceImpl}.
 * shortcut: a decorator outside service/impl because that folder was unreachable when this was written —
 * fold the FREE branch into CreditServiceImpl when convenient.
 */
@Service
@Primary
@RequiredArgsConstructor
public class FreeQuotaCreditService implements CreditService {

    public static final String FREE_PLAN = "FREE";
    public static final int FREE_USES = 3;

    private final CreditServiceImpl credits;
    private final UserMembershipRepository memberships;

    public record FreeQuota(boolean freePlan, int used, int limit) {
    }

    @Override
    @Transactional
    public UserMembership getOrCreateMembership(UUID userId) {
        return credits.getOrCreateMembership(userId);
    }

    @Override
    public int creditCost(String taskType) {
        return credits.creditCost(taskType);
    }

    @Override
    @Transactional
    public void consume(UUID userId, String taskType, String referenceType, UUID referenceId) {
        UserMembership membership = credits.getOrCreateMembership(userId);
        if (!isFree(membership)) {
            credits.consume(userId, taskType, referenceType, referenceId);
            return;
        }
        if (credits.creditCost(taskType) == 0) {
            return; // free operations (e.g. uploading your own model) don't use up a generation
        }
        if (memberships.takeFreeUse(membership.getId(), FREE_USES) == 0) {
            throw new BadRequestException("credit.freeUsesExhausted");
        }
    }

    @Override
    @Transactional
    public void refund(UUID userId, int amount, String referenceType, UUID referenceId) {
        UserMembership membership = credits.getOrCreateMembership(userId);
        if (isFree(membership)) {
            memberships.returnFreeUse(membership.getId());
        } else {
            credits.refund(userId, amount, referenceType, referenceId);
        }
    }

    @Override
    @Transactional
    public void activatePlan(UUID userId, String planCode) {
        credits.activatePlan(userId, planCode);
    }

    @Transactional
    public FreeQuota freeQuota(UUID userId) {
        UserMembership membership = credits.getOrCreateMembership(userId);
        return new FreeQuota(isFree(membership), membership.getFreeUses(), FREE_USES);
    }

    private static boolean isFree(UserMembership membership) {
        return FREE_PLAN.equals(membership.getPlan().getCode());
    }
}
