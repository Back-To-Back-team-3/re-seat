package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.Getter;

/** PG 승인 후 로컬 결제 상태 반영에 실패했을 때 발생한다. */
@Getter
public class PaymentLocalApplyFailedException extends BusinessException {

    /** 복구할 결제 ID. */
    private final Long paymentId;

    /** 실패 상태를 전파할 주문 ID. */
    private final Long orderId;

    /** PG 승인 및 보상 취소에 사용할 결제 키. */
    private final String paymentKey;

    /** 복구에 필요한 결제 정보와 원인 예외를 보존한다. */
    public PaymentLocalApplyFailedException(Long paymentId, Long orderId, String paymentKey, Throwable cause) {
        super(ErrorCode.PAYMENT_LOCAL_APPLY_FAILED, cause);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
    }
}
