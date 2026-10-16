package com.innerstyle.print.entity;

import com.innerstyle.print.entity.enums.PrintOrderStatus;
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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** One step on a print order's tracking timeline (placed, paid, staff updates, …). */
@Entity
@Table(name = "dtb_print_order_events")
@Getter
@Setter
@NoArgsConstructor
public class PrintOrderEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "print_order_id", nullable = false)
    private UUID printOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrintOrderStatus status;

    /** Free text from staff, or a {@code system.*} key the frontend translates; null for a plain status change. */
    @Column(length = 500)
    private String note;

    /** Staff member who posted the update; null for system events (order placed, payment, expiry). */
    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
