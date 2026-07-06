package com.innerstyle.membership.service;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.membership.entity.MembershipPlan;
import com.innerstyle.membership.entity.OperationCredit;
import com.innerstyle.membership.entity.UserMembership;
import com.innerstyle.membership.entity.enums.MembershipStatus;
import com.innerstyle.membership.repository.CreditTransactionRepository;
import com.innerstyle.membership.repository.MembershipPlanRepository;
import com.innerstyle.membership.repository.OperationCreditRepository;
import com.innerstyle.membership.repository.UserMembershipRepository;
import com.innerstyle.membership.service.impl.CreditServiceImpl;
import com.innerstyle.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreditServiceImpl}: consume/refund/activate mutate the balance under a
 * pessimistic lock and append an immutable ledger entry; insufficient balance is rejected.
 */
class CreditServiceImplTest {

    private UserRepository userRepository;
    private MembershipPlanRepository planRepository;
    private OperationCreditRepository operationCreditRepository;
    private UserMembershipRepository membershipRepository;
    private CreditTransactionRepository creditTransactionRepository;
    private CreditServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        planRepository = mock(MembershipPlanRepository.class);
        operationCreditRepository = mock(OperationCreditRepository.class);
        membershipRepository = mock(UserMembershipRepository.class);
        creditTransactionRepository = mock(CreditTransactionRepository.class);
        service = new CreditServiceImpl(userRepository, planRepository, operationCreditRepository,
            membershipRepository, creditTransactionRepository);
    }

    private MembershipPlan plan(String code, int monthlyCredits) {
        MembershipPlan p = new MembershipPlan();
        p.setCode(code);
        p.setMonthlyCredits(monthlyCredits);
        return p;
    }

    private UserMembership membership(int credits) {
        UserMembership m = new UserMembership();
        m.setId(UUID.randomUUID());
        m.setPlan(plan("PRO", 500));
        m.setCreditsRemaining(credits);
        m.setPeriodStart(Instant.now());
        m.setPeriodEnd(Instant.now().plusSeconds(86400)); // future → no lazy renewal
        m.setStatus(MembershipStatus.ACTIVE);
        return m;
    }

    private OperationCredit opCredit(int cost) {
        OperationCredit oc = new OperationCredit();
        oc.setTaskType("TEXT_TO_3D");
        oc.setCreditCost(cost);
        oc.setActive(true);
        return oc;
    }

    private void stubLocked(UserMembership m) {
        when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(m));
        when(membershipRepository.findByIdForUpdate(m.getId())).thenReturn(Optional.of(m));
    }

    @Test
    @DisplayName("consume: sufficient balance → decrements and writes ledger")
    void consume_sufficient() {
        UserMembership m = membership(100);
        when(operationCreditRepository.findByTaskTypeAndActiveTrue("TEXT_TO_3D"))
            .thenReturn(Optional.of(opCredit(10)));
        stubLocked(m);

        service.consume(userId, "TEXT_TO_3D", "MESHY_TASK", UUID.randomUUID());

        assertThat(m.getCreditsRemaining()).isEqualTo(90);
        verify(membershipRepository).save(m);
        verify(creditTransactionRepository).save(any());
    }

    @Test
    @DisplayName("consume: insufficient balance → credit.insufficient, no decrement")
    void consume_insufficient() {
        UserMembership m = membership(5);
        when(operationCreditRepository.findByTaskTypeAndActiveTrue("TEXT_TO_3D"))
            .thenReturn(Optional.of(opCredit(10)));
        stubLocked(m);

        assertThatThrownBy(() -> service.consume(userId, "TEXT_TO_3D", "MESHY_TASK", UUID.randomUUID()))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("credit.insufficient");
        assertThat(m.getCreditsRemaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("consume: zero-cost operation → no-op, no membership access")
    void consume_zeroCost() {
        when(operationCreditRepository.findByTaskTypeAndActiveTrue("FREE_OP"))
            .thenReturn(Optional.empty());

        service.consume(userId, "FREE_OP", "MESHY_TASK", UUID.randomUUID());

        verify(membershipRepository, never()).findByIdForUpdate(any());
        verify(creditTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refund: positive amount → increments and writes ledger")
    void refund_positive() {
        UserMembership m = membership(20);
        stubLocked(m);

        service.refund(userId, 15, "MESHY_TASK", UUID.randomUUID());

        assertThat(m.getCreditsRemaining()).isEqualTo(35);
        verify(creditTransactionRepository).save(any());
    }

    @Test
    @DisplayName("refund: non-positive amount → no-op")
    void refund_nonPositive() {
        service.refund(userId, 0, "MESHY_TASK", UUID.randomUUID());
        verify(membershipRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("activatePlan: sets credits to plan allowance, ACTIVE, resets period, GRANT ledger")
    void activatePlan_success() {
        UserMembership m = membership(3);
        when(planRepository.findByCode("MAX")).thenReturn(Optional.of(plan("MAX", 2000)));
        stubLocked(m);

        service.activatePlan(userId, "MAX");

        assertThat(m.getCreditsRemaining()).isEqualTo(2000);
        assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(creditTransactionRepository).save(any());
    }

    @Test
    @DisplayName("activatePlan: unknown plan → membership.plan.notFound")
    void activatePlan_unknownPlan() {
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activatePlan(userId, "GHOST"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("membership.plan.notFound");
    }
}
