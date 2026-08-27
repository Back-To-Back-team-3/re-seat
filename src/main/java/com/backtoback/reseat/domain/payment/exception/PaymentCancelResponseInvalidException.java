package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/** PG 결제 취소 응답에서 취소 완료 상태를 확인할 수 없을 때 발생한다. */
public class PaymentCancelResponseInvalidException extends BusinessException {

    /** 결제 취소 응답 오류 코드로 예외를 생성한다. */
    public PaymentCancelResponseInvalidException() {
        super(ErrorCode.PAYMENT_CANCEL_RESPONSE_INVALID);
    }
}
