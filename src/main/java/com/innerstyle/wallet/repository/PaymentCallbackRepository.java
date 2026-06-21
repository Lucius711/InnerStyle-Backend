package com.innerstyle.wallet.repository;

import com.innerstyle.wallet.entity.PaymentCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentCallbackRepository extends JpaRepository<PaymentCallback, UUID> {
}
