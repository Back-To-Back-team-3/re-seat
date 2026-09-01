package com.backtoback.reseat.domain.payment.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 티켓 단위 PG 결제 취소 이력을 관리한다.
 */
@Entity
@Table(
    name = "payment_cancels",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_cancels_ticket",
            columnNames = "ticket_id"
        ),
        @UniqueConstraint(
            name = "uk_payment_cancels_pg_idempotency_key",
            columnNames = "pg_idempotency_key"
        )
    },
    indexes = {
        @Index(
            name = "idx_payment_cancels_payment_status",
            columnList = "payment_id, status"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel extends BaseEntity {

    /**
     * 기본키 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 취소 대상 결제.
     */
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_cancels_payment")
    )
    private Payment payment;

    /**
     * 취소 대상 티켓.
     */
    @OneToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "ticket_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_cancels_ticket")
    )
    private Ticket ticket;

    /**
     * PG 결제 취소 처리 상태.
     */
    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 20
    )
    private PaymentCancelStatus status;

    /**
     * PG에 전달할 결제 취소 사유.
     */
    @Column(
        nullable = false,
        length = 200
    )
    private String reason;

    /**
     * 현재 PG 취소 시도를 식별하는 멱등키.
     */
    @Column(
        name = "pg_idempotency_key",
        length = 200
    )
    private String pgIdempotencyKey;

    /**
     * PG가 반환한 취소 거래 식별자.
     */
    @Column(
        name = "pg_transaction_key",
        length = 200
    )
    private String pgTransactionKey;

    /**
     * PG 결제 취소 실패 사유.
     */
    @Column(
        name = "failure_reason",
        length = 500
    )
    private String failureReason;

    /**
     * PG 결제 취소 완료 시각.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * PG 결제 취소 실패 시각.
     */
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /**
     * 티켓 한 장의 결제 취소 이력을 대기 상태로 생성하고 결제에 연결한다.
     */
    public static PaymentCancel create(Payment payment, Ticket ticket, String reason, String pgIdempotencyKey) {
        validatePayment(payment);
        validateTicket(ticket);
        validateReason(reason);
        validatePgIdempotencyKey(pgIdempotencyKey);

        PaymentCancel paymentCancel = new PaymentCancel();
        paymentCancel.payment = payment;
        paymentCancel.ticket = ticket;
        paymentCancel.status = PaymentCancelStatus.PENDING;
        paymentCancel.reason = reason;
        paymentCancel.pgIdempotencyKey = pgIdempotencyKey;
        payment.addCancel(paymentCancel);
        return paymentCancel;
    }

    /**
     * 결제 취소 이력에 결제가 연결되었는지 검증한다.
     */
    private static void validatePayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("결제는 필수입니다.");
        }
    }

    /**
     * 결제 취소 이력에 티켓이 연결되었는지 검증한다.
     */
    private static void validateTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("티켓은 필수입니다.");
        }
    }

    /**
     * 결제 취소 사유가 입력되었는지 검증한다.
     */
    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("취소 사유는 필수입니다.");
        }
    }

    /**
     * PG 취소 시도를 식별할 멱등키가 입력되었는지 검증한다.
     */
    private static void validatePgIdempotencyKey(String pgIdempotencyKey) {
        if (pgIdempotencyKey == null || pgIdempotencyKey.isBlank()) {
            throw new IllegalArgumentException("PG 멱등키는 필수입니다.");
        }
    }

    /**
     * PG 취소 완료 정보를 기록한다.
     */
    public void complete(String pgTransactionKey, LocalDateTime completedAt) {
        validateStatus(PaymentCancelStatus.PENDING);
        if (pgTransactionKey == null || pgTransactionKey.isBlank() || completedAt == null) {
            throw new IllegalArgumentException("PG 거래 키와 완료 시각은 필수입니다.");
        }

        this.status = PaymentCancelStatus.DONE;
        this.pgTransactionKey = pgTransactionKey;
        this.completedAt = completedAt;
        this.failureReason = null;
        this.failedAt = null;
    }

    /**
     * PG 취소 실패 정보와 시각을 기록한다.
     */
    public void fail(String failureReason, LocalDateTime failedAt) {
        validateStatus(PaymentCancelStatus.PENDING);
        if (failureReason == null || failureReason.isBlank() || failedAt == null) {
            throw new IllegalArgumentException("실패 사유와 실패 시각은 필수입니다.");
        }

        this.status = PaymentCancelStatus.FAILED;
        this.failureReason = failureReason;
        this.failedAt = failedAt;
    }

    /**
     * 실패한 결제 취소 이력을 새로운 사유로 다시 대기 상태로 전환한다.
     */
    public void retry(String reason, String pgIdempotencyKey) {
        validateStatus(PaymentCancelStatus.FAILED);
        validateReason(reason);
        validatePgIdempotencyKey(pgIdempotencyKey);

        this.status = PaymentCancelStatus.PENDING;
        this.reason = reason;
        this.pgIdempotencyKey = pgIdempotencyKey;
        this.pgTransactionKey = null;
        this.failureReason = null;
        this.completedAt = null;
        this.failedAt = null;
    }

    /**
     * PG 취소가 완료된 이력인지 확인한다.
     */
    public boolean isDone() {
        return status == PaymentCancelStatus.DONE;
    }

    /**
     * 현재 상태에서 요청한 상태 전이가 가능한지 검증한다.
     */
    private void validateStatus(PaymentCancelStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("결제 취소 이력 상태를 변경할 수 없습니다.");
        }
    }
}
