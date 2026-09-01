package com.backtoback.reseat.domain.payment.entity;

/** 결제 복구 작업이 처리할 PG 연동 상황을 구분한다. */
public enum PaymentRecoveryType {
    CONFIRM_UNKNOWN, PARTIAL_CANCEL
}
