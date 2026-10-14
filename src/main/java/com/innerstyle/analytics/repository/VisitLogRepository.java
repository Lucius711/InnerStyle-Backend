package com.innerstyle.analytics.repository;

import com.innerstyle.analytics.entity.VisitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface VisitLogRepository extends JpaRepository<VisitLog, UUID> {

    /** Distinct signed-in visitors per local day; {@code day} is ISO yyyy-MM-dd. */
    interface DailyActiveUsers {
        String getDay();

        long getUsers();
    }

    @Query(value = "SELECT to_char(created_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day, "
            + "COUNT(DISTINCT user_id) AS users "
            + "FROM dtb_visit_logs WHERE user_id IS NOT NULL AND created_at >= :from "
            + "GROUP BY 1",
            nativeQuery = true)
    List<DailyActiveUsers> countDailyActiveUsers(@Param("from") Instant from, @Param("zone") String zone);
}
