package com.innerstyle.auth.repository;

import com.innerstyle.auth.entity.PasswordResetToken;
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
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now "
        + "WHERE t.user = :user AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("user") User user, @Param("now") Instant now);
}
