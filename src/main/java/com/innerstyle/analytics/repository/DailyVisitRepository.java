package com.innerstyle.analytics.repository;

import com.innerstyle.analytics.entity.DailyVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyVisitRepository extends JpaRepository<DailyVisit, LocalDate> {

    /** Atomic upsert: safe under concurrent visits, no read-then-write race. */
    @Modifying
    @Query(value = "INSERT INTO dtb_daily_visits (visit_date, visit_count) VALUES (:day, 1) "
            + "ON CONFLICT (visit_date) DO UPDATE SET visit_count = dtb_daily_visits.visit_count + 1",
            nativeQuery = true)
    void increment(@Param("day") LocalDate day);

    List<DailyVisit> findByVisitDateGreaterThanEqualOrderByVisitDateAsc(LocalDate from);
}
