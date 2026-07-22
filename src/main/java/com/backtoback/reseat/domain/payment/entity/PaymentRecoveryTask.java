package com.backtoback.reseat.domain.payment.entity;

import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "payment_recovery_tasks",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_payment_recovery_tasks_payment", columnNames = "payment_id")
        },
        indexes = {
            @Index(
                    name = "idx_payment_recovery_tasks_status_retry",
                    columnList = "status, next_retry_at"
            )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRecoveryTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_recovery_tasks_payment")
    )
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRecoveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public PaymentRecoveryTask(
            Payment payment,
            PaymentRecoveryStatus status,
            int attemptCount,
            LocalDateTime nextRetryAt,
            LocalDateTime processingStartedAt,
            String lastError,
            LocalDateTime completedAt) {
        this.payment = payment;
        this.status = status != null ? status : PaymentRecoveryStatus.PENDING;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.processingStartedAt = processingStartedAt;
        this.lastError = lastError;
        this.completedAt = completedAt;
    }
}
