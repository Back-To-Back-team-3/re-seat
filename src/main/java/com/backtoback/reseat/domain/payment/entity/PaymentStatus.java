// 결제 상태 enum

package com.backtoback.reseat.domain.payment.entity;

public enum PaymentStatus {
    READY,
    PENDING,
    APPROVED,
    FAILED,
    CANCELED
}
