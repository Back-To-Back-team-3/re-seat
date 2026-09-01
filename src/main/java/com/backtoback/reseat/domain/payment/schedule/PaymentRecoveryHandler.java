package com.backtoback.reseat.domain.payment.schedule;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;

/** 결제 복구 유형별 처리기가 따라야 하는 계약이다. */
public interface PaymentRecoveryHandler {

    /** 처리기가 지원하는 복구 유형을 반환한다. */
    PaymentRecoveryType getType();

    /** PG 상태를 확인하고 복구 처리 결과를 반환한다. */
    PaymentRecoveryResult recover(PaymentRecoveryTask task);
}
