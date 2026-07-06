// 결제 메인 엔티티 (payments 테이블 매핑)

package com.backtoback.reseat.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// JPA 엔티티 + Lombok 기본 설정
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    // 기본 키 (PK)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 결제 번호
    @Column(name = "payment_no", nullable = false, unique = true, length = 50)
    private String paymentNo;

    // 주문 ID
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // 사용자 ID
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 결제 금액
    @Column(nullable = false)
    private Integer amount;

    // 결제 수단
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    // 결제 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // 토스 등 외부 결제 시스템 키
    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    // 결제 실패 사유
    @Column(name = "fail_reason", length = 200)
    private String failReason;

    // 결제 승인 시각
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 생성/수정 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // DB 저장 직전 기본값 세팅
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
    }

    // DB 수정 직전 수정 시간 갱신
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 빌더 생성자
    @Builder
    public Payment(String paymentNo, Long orderId, Long userId, Integer amount,
                   PaymentMethod method, PaymentStatus status, String paymentKey,
                   String failReason, LocalDateTime approvedAt) {
        this.paymentNo = paymentNo;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.method = method;
        this.status = status != null ? status : PaymentStatus.PENDING;
        this.paymentKey = paymentKey;
        this.failReason = failReason;
        this.approvedAt = approvedAt;
    }

    // 결제 승인 처리
    public void approve(String paymentKey) {
        this.status = PaymentStatus.APPROVED;
        this.paymentKey = paymentKey;
        this.approvedAt = LocalDateTime.now();
    }

    // 결제 실패 처리
    public void fail(String failReason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason;
    }
}
