package com.innerstyle.wallet.repository;

import com.innerstyle.wallet.entity.PaymentOrder;
import com.innerstyle.wallet.entity.enums.PaymentPurpose;
import com.innerstyle.wallet.entity.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByOrderCode(String orderCode);

    /**
     * Loads an order with a pessimistic write lock (SELECT ... FOR UPDATE) so concurrent /
     * retried gateway callbacks for the same order are serialized and cannot both settle
     * (review finding C1). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderCode = :orderCode")
    Optional<PaymentOrder> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Latest payment still awaiting the gateway for a given purpose/reference (e.g. a print order). */
    Optional<PaymentOrder> findFirstByPurposeAndReferenceAndStatusOrderByCreatedAtDesc(
        PaymentPurpose purpose, String reference, PaymentStatus status);
}
