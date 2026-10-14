package com.innerstyle.auth.repository;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Case-insensitive email lookup (matches the UNIQUE(LOWER(email)) index). */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT (COUNT(u) > 0) FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    long countByStatus(UserStatus status);

    /** Accounts created per local day; {@code day} is ISO yyyy-MM-dd. */
    interface DailyCount {
        String getDay();

        long getUsers();
    }

    @Query(value = "SELECT to_char(created_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day, COUNT(*) AS users "
            + "FROM dtb_users WHERE created_at >= :from GROUP BY 1",
            nativeQuery = true)
    List<DailyCount> countNewUsersPerDay(@Param("from") Instant from, @Param("zone") String zone);

    // last_seen_at is deliberately not mapped on User: a stale entity save would overwrite it.
    @Modifying
    @Query(value = "UPDATE dtb_users SET last_seen_at = NOW() WHERE id = :id", nativeQuery = true)
    void touchLastSeen(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE dtb_users SET last_seen_at = NULL WHERE id = :id", nativeQuery = true)
    void clearLastSeen(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM dtb_users WHERE last_seen_at > :since", nativeQuery = true)
    long countSeenSince(@Param("since") Instant since);
}
