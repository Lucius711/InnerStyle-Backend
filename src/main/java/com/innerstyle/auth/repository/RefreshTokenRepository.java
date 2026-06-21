package com.innerstyle.auth.repository;

import com.innerstyle.auth.entity.RefreshToken;
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
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke every active token of a user (e.g. after a password reset). */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now "
        + "WHERE r.user = :user AND r.revokedAt IS NULL")
    int revokeAllForUser(@Param("user") User user, @Param("now") Instant now);
}
