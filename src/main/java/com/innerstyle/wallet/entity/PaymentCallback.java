package com.innerstyle.wallet.entity;

import com.innerstyle.wallet.entity.enums.PaymentCallbackKind;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Raw gateway callback (IPN / return) stored for audit and replay-defence. Kept even if the
 * referenced order is later deleted (FK ON DELETE SET NULL).
 */
@Entity
@Table(name = "dtb_payment_callbacks")
@Getter
@Setter
@NoArgsConstructor
public class PaymentCallback {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "payment_order_id")
    private UUID paymentOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentCallbackKind kind = PaymentCallbackKind.IPN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> rawPayload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "response_code", length = 20)
    private String responseCode;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false, nullable = false)
    private Instant receivedAt;
}
