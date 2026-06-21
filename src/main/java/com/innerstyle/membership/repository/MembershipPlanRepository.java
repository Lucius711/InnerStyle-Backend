package com.innerstyle.membership.repository;

import com.innerstyle.membership.entity.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Integer> {

    Optional<MembershipPlan> findByCode(String code);

    List<MembershipPlan> findByActiveTrueOrderBySortOrderAsc();
}
