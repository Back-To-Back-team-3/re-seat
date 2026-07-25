// 결제 메인 엔티티 (payments 테이블 매핑)

package com.backtoback.reseat.domain.payment.entity;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCallbackMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelNotAllowedException;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// JPA 엔티티 + Lombok 기본 설정
@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_no", columnNames = "payment_no"),
        @UniqueConstraint(name = "uk_payments_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_payments_pg_payment_key", columnNames = "pg_payment_key")
    },
    indexes = {
        @Index(name = "idx_payments_user_status", columnList = "user_id, status")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    // 기본 키 (PK)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 서비스 내부 결제 번호
    @Column(name = "payment_no", nullable = false, length = 50)
    private String paymentNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payments_order")
    )
    private Order order;

    // 결제 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 결제 금액
    @Column(nullable = false)
    private Integer amount;

    // 결제 수단
    @Column(length = 20)
    private String method;

    // 결제 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // 중복 결제 방지 키
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    // 결제 승인/취소를 요청할 PG사
    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 20)
    private PgProvider pgProvider;

    // PG에 전달한 주문 식별자
    @Column(name = "pg_order_id", nullable = false, length = 100)
    private String pgOrderId;

    // PG가 발급한 결제 키
    @Column(name = "pg_payment_key", length = 200)
    private String pgPaymentKey;

    // 결제 실패 사유
    @Column(name = "fail_reason", length = 200)
    private String failReason;

    // 결제 승인 시각
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 결제 실패 시각
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    // 빌더 생성자
    @Builder
    public Payment(String paymentNo, Order order, User user, Integer amount,
                   String method, PaymentStatus status, String idempotencyKey,
                   PgProvider pgProvider, String pgOrderId, String pgPaymentKey,
                   String failReason, LocalDateTime approvedAt, LocalDateTime failedAt) {
        this.paymentNo = paymentNo;
        this.order = order;
        this.user = user;
        this.amount = amount;
        this.method = method;
        this.status = status != null ? status : PaymentStatus.READY;
        this.idempotencyKey = idempotencyKey;
        this.pgProvider = pgProvider != null ? pgProvider : PgProvider.MOCK;
        this.pgOrderId = pgOrderId;
        this.pgPaymentKey = pgPaymentKey;
        this.failReason = failReason;
        this.approvedAt = approvedAt;
        this.failedAt = failedAt;
    }

    /** Toss 승인 요청에 사용할 PG 결제 키를 연결한다. */
    public void assignPgPaymentKey(String pgPaymentKey) {
        if (this.pgPaymentKey != null && !this.pgPaymentKey.equals(pgPaymentKey)) {
            throw new PaymentCallbackMismatchException();
        }
        this.pgPaymentKey = pgPaymentKey;
    }

    // 결제 승인 처리
    public void approve(String method, LocalDateTime approvedAt) {
        this.status = PaymentStatus.APPROVED;
        this.method = method;
        this.approvedAt = approvedAt;
        this.failReason = null;
        this.failedAt = null;
    }

    // 결제 실패 처리
    public void fail(String failReason, LocalDateTime failedAt) {
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason;
        this.failedAt = failedAt;
    }

    // 동일 주문의 READY 결제를 재사용할 때 현재 결제 시도의 멱등키를 교체한다.
    public void changeIdempotencyKey(String idempotencyKey) {
        if (status != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        this.idempotencyKey = idempotencyKey;
    }

    // 결제 취소 처리
    public void cancel() {
        if (status != PaymentStatus.APPROVED) {
            throw new PaymentCancelNotAllowedException();
        }
        this.status = PaymentStatus.CANCELED;
        this.failReason = null;
        this.failedAt = null;
    }
}
