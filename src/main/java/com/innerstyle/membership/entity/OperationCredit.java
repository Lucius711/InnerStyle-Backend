package com.innerstyle.membership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Credit cost of a 3D operation (e.g. IMAGE_TO_3D = 5).
 */
@Entity
@Table(name = "mtb_operation_credits")
@Getter
@Setter
@NoArgsConstructor
public class OperationCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "task_type", nullable = false, unique = true, length = 32)
    private String taskType;

    @Column(name = "credit_cost", nullable = false)
    private int creditCost;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
