package com.innerstyle.print.entity;

import com.innerstyle.auth.entity.User;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A request to 3D-print a finished model. A flat fee is debited from the wallet when the order
 * is placed (status PAID). {@code sourceTaskId} points to the Meshy task that produced the model.
 */
@Entity
@Table(name = "dtb_print_orders")
@Getter
@Setter
@NoArgsConstructor
public class PrintOrder {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_task_id")
    private UUID sourceTaskId;

    /** Chosen figurine height in cm (drives the price). */
    @Column(name = "size_cm")
    private Integer sizeCm;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrintOrderStatus status = PrintOrderStatus.PAID;

    @Column(length = 500)
    private String note;

    // ----- Recipient (the customer who placed the order) -----

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    // ----- Shipping address (Vietnam 2-level: province + ward) -----

    @Column(name = "province_code", length = 20)
    private String provinceCode;

    @Column(name = "province_name", length = 150)
    private String provinceName;

    @Column(name = "ward_code", length = 20)
    private String wardCode;

    @Column(name = "ward_name", length = 150)
    private String wardName;

    @Column(name = "address_detail", length = 500)
    private String addressDetail;

    /** Optional pinned coordinates from Google Maps (decimal degrees). */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
