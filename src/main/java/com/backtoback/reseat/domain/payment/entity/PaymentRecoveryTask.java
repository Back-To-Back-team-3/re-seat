package com.backtoback.reseat.domain.payment.entity;

import java.time.LocalDateTime;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "payment_recovery_tasks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_recovery_tasks_recovery_key",
            columnNames = "recovery_key"
        )
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

    private static final String RECOVERY_KEY_SEPARATOR = ":";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_recovery_tasks_payment")
    )
    private Payment payment;

    /** 부분 취소 복구 작업이 처리할 결제 취소 이력. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "payment_cancel_id",
        foreignKey = @ForeignKey(name = "fk_payment_recovery_tasks_payment_cancel")
    )
    private PaymentCancel paymentCancel;

    /** 복구 작업이 처리할 PG 연동 상황. */
    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 40
    )
    private PaymentRecoveryType type;

    /** 복구 유형과 대상 ID를 조합한 중복 방지 키. */
    @Column(
        name = "recovery_key",
        nullable = false,
        length = 100
    )
    private String recoveryKey;

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 20
    )
    private PaymentRecoveryStatus status;

    @Column(
        name = "attempt_count",
        nullable = false
    )
    private int attemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(
        name = "last_error",
        length = 500
    )
    private String lastError;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 승인 상태를 확인할 수 없는 결제의 복구 작업을 생성한다. */
    public static PaymentRecoveryTask createConfirmUnknown(Payment payment) {
        validateId(payment == null ? null : payment.getId(), "결제");
        return create(payment, null, PaymentRecoveryType.CONFIRM_UNKNOWN, payment.getId());
    }

    /** 처리 결과를 확인할 수 없는 부분 취소의 복구 작업을 생성한다. */
    public static PaymentRecoveryTask createPartialCancel(PaymentCancel paymentCancel) {
        validateId(paymentCancel == null ? null : paymentCancel.getId(), "결제 취소 이력");
        if (paymentCancel.getPayment() == null) {
            throw new IllegalArgumentException("결제 취소 이력에 결제가 연결되어 있어야 합니다.");
        }
        return create(
            paymentCancel.getPayment(),
            paymentCancel,
            PaymentRecoveryType.PARTIAL_CANCEL,
            paymentCancel.getId()
        );
    }

    private static PaymentRecoveryTask create(
        Payment payment,
        PaymentCancel paymentCancel,
        PaymentRecoveryType type,
        Long targetId
    ) {
        PaymentRecoveryTask task = new PaymentRecoveryTask();
        task.payment = payment;
        task.paymentCancel = paymentCancel;
        task.type = type;
        task.recoveryKey = type.name() + RECOVERY_KEY_SEPARATOR + targetId;
        task.status = PaymentRecoveryStatus.PENDING;
        task.attemptCount = 0;
        return task;
    }

    private static void validateId(Long id, String target) {
        if (id == null) {
            throw new IllegalArgumentException(target + " ID는 필수입니다.");
        }
    }

    /**
     * 대기 또는 재시도 중인 복구 작업을 처리 중 상태로 전환한다.
     */
    public void startProcessing(LocalDateTime processingStartedAt) {
        if (status != PaymentRecoveryStatus.PENDING && status != PaymentRecoveryStatus.RETRY) {
            throw new IllegalStateException("대기 또는 재시도 중인 결제 복구 작업만 처리할 수 있습니다.");
        }
        this.status = PaymentRecoveryStatus.PROCESSING;
        this.nextRetryAt = null;
        this.processingStartedAt = processingStartedAt;
    }

    /**
     * 복구 실패를 기록하고 다음 자동 재시도를 예약한다.
     */
    public void scheduleRetry(String lastError, LocalDateTime nextRetryAt) {
        validateProcessing();
        this.attemptCount++;
        this.status = PaymentRecoveryStatus.RETRY;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.processingStartedAt = null;
    }

    /**
     * 복구 작업을 완료하고 처리 중·재시도 정보를 정리한다.
     */
    public void complete(LocalDateTime completedAt) {
        validateProcessing();
        this.status = PaymentRecoveryStatus.COMPLETED;
        this.nextRetryAt = null;
        this.processingStartedAt = null;
        this.lastError = null;
        this.completedAt = completedAt;
    }

    /**
     * 자동 복구할 수 없는 작업을 최종 실패 처리한다.
     */
    public void fail(String lastError) {
        validateProcessing();
        this.status = PaymentRecoveryStatus.FAILED;
        this.nextRetryAt = null;
        this.processingStartedAt = null;
        this.lastError = lastError;
    }

    private void validateProcessing() {
        if (status != PaymentRecoveryStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 결제 복구 작업만 상태를 변경할 수 있습니다.");
        }
    }
}
