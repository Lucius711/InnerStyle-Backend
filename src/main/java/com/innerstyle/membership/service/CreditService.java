package com.innerstyle.membership.service;

import com.innerstyle.membership.entity.UserMembership;

import java.util.UUID;

/**
 * Credit accounting for the membership model. Every 3D operation consumes credits from the user's
 * monthly allowance; failed jobs are refunded. Buying a plan grants that plan's monthly credits.
 */
public interface CreditService {

    /** The user's membership, auto-creating a FREE one and renewing the period if it lapsed. */
    UserMembership getOrCreateMembership(UUID userId);

    /** Credit cost of an operation (0 = free / not billable). */
    int creditCost(String taskType);

    /** Deduct credits for an operation. Throws {@code credit.insufficient} if not enough. */
    void consume(UUID userId, String taskType, String referenceType, UUID referenceId);

    /** Return credits (e.g. the job failed). */
    void refund(UUID userId, int amount, String referenceType, UUID referenceId);

    /** Activate / renew a plan after a successful subscription payment. */
    void activatePlan(UUID userId, String planCode);
}
