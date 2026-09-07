package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/** PG 재조회 후에도 결제 승인 상태를 확인할 수 없을 때 발생한다. */
public class PaymentConfirmStatusUnknownException extends BusinessException {

    /** 결제 승인 상태 확인 불가 에러 코드로 예외를 생성한다. */
    public PaymentConfirmStatusUnknownException() {
        super(ErrorCode.PAYMENT_CONFIRM_STATUS_UNKNOWN);
    }
}
