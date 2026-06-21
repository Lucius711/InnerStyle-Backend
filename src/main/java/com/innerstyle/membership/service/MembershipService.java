package com.innerstyle.membership.service;

import com.innerstyle.membership.dto.response.MembershipResponse;
import com.innerstyle.membership.dto.response.OperationCreditResponse;
import com.innerstyle.membership.dto.response.PlanResponse;

import java.util.List;
import java.util.UUID;

/** Read-side membership queries (the current membership, plans, operation credit costs). */
public interface MembershipService {

    MembershipResponse getMyMembership(UUID userId);

    List<PlanResponse> listPlans();

    List<OperationCreditResponse> listOperationCredits();
}
