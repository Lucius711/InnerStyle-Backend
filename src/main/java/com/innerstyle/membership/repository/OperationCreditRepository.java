package com.innerstyle.membership.repository;

import com.innerstyle.membership.entity.OperationCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperationCreditRepository extends JpaRepository<OperationCredit, Integer> {

    Optional<OperationCredit> findByTaskTypeAndActiveTrue(String taskType);
}
