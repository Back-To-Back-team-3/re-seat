package com.backtoback.reseat.domain.payment.schedule;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;

/** 결제 복구 유형별 처리기가 따라야 하는 계약이다. */
public interface PaymentRecoveryHandler {

    /** 처리기가 지원하는 복구 유형을 반환한다. */
    PaymentRecoveryType getType();

    /** PG 상태를 확인하고 복구 처리 결과를 반환한다. */
    PaymentRecoveryResult recover(PaymentRecoveryTask task);

    /** 재시도 횟수를 모두 소진한 작업의 복구 대상 상태를 최종 실패로 전이한다. */
    default void handleFinalFailure(PaymentRecoveryTask task, String error, LocalDateTime failedAt) {
        // 복구 유형별 최종 실패 후처리가 필요한 Handler만 재정의한다.
    }
}
