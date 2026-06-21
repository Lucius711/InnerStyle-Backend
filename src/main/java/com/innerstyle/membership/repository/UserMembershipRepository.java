package com.innerstyle.membership.repository;

import com.innerstyle.membership.entity.UserMembership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
