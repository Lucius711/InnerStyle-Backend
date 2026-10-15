package com.innerstyle.membership.repository;

import com.innerstyle.membership.entity.UserMembership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMembershipRepository extends JpaRepository<UserMembership, UUID> {

    Optional<UserMembership> findByUserId(UUID userId);

    /** Pessimistic row lock for the credit-mutation critical section. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM UserMembership m WHERE m.id = :id")
    Optional<UserMembership> findByIdForUpdate(@Param("id") UUID id);

    /** Atomically take one FREE use; returns 0 when the member has already used {@code limit}. */
    @Modifying
    @Query("UPDATE UserMembership m SET m.freeUses = m.freeUses + 1 WHERE m.id = :id AND m.freeUses < :limit")
    int takeFreeUse(@Param("id") UUID id, @Param("limit") int limit);

    /** Give one FREE use back (the job failed). */
    @Modifying
    @Query("UPDATE UserMembership m SET m.freeUses = m.freeUses - 1 WHERE m.id = :id AND m.freeUses > 0")
    int returnFreeUse(@Param("id") UUID id);
}
