package com.innerstyle.auth.repository;

import com.innerstyle.auth.entity.EmailVerificationToken;
import com.innerstyle.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Invalidate any outstanding tokens before issuing a new one. */
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now "
        + "WHERE t.user = :user AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("user") User user, @Param("now") Instant now);
}
