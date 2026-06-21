package com.innerstyle.membership.service.impl;

import com.innerstyle.membership.dto.response.MembershipResponse;
import com.innerstyle.membership.dto.response.OperationCreditResponse;
import com.innerstyle.membership.dto.response.PlanResponse;
import com.innerstyle.membership.entity.UserMembership;
import com.innerstyle.membership.repository.MembershipPlanRepository;
import com.innerstyle.membership.repository.OperationCreditRepository;
import com.innerstyle.membership.service.CreditService;
import com.innerstyle.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final CreditService creditService;
    private final MembershipPlanRepository planRepository;
    private final OperationCreditRepository operationCreditRepository;

    @Override
    @Transactional
    public MembershipResponse getMyMembership(UUID userId) {
        UserMembership m = creditService.getOrCreateMembership(userId);
        return new MembershipResponse(
            m.getPlan().getCode(), m.getPlan().getName(), m.getCreditsRemaining(),
            m.getPlan().getMonthlyCredits(), m.getPeriodEnd(), m.getStatus().name());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(p -> new PlanResponse(p.getCode(), p.getName(), p.getMonthlyCredits(),
                p.getPrice(), p.getCurrency()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationCreditResponse> listOperationCredits() {
        return operationCreditRepository.findAll().stream()
            .filter(c -> c.isActive())
            .map(c -> new OperationCreditResponse(c.getTaskType(), c.getCreditCost()))
            .toList();
    }
}
