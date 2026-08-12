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
import jakarta.persistence.OneToOne;
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
            name = "uk_payment_recovery_tasks_payment",
            columnNames = "payment_id"
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

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(
	    fetch = FetchType.LAZY,
	    optional = false
	)
	@JoinColumn(
	    name = "payment_id",
	    nullable = false,
	    foreignKey = @ForeignKey(name = "fk_payment_recovery_tasks_payment")
	)
	private Payment payment;

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

	public PaymentRecoveryTask(Payment payment) {
		this.payment = payment;
		this.status = PaymentRecoveryStatus.PENDING;
		this.attemptCount = 0;
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
