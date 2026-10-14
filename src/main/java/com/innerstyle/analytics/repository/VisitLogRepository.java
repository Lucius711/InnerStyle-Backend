package com.innerstyle.analytics.repository;

import com.innerstyle.analytics.entity.VisitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VisitLogRepository extends JpaRepository<VisitLog, UUID> {
}
