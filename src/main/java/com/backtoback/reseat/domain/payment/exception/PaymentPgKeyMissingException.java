package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/** 결제 취소에 필요한 PG 결제 키가 없을 때 발생한다. */
public class PaymentPgKeyMissingException extends BusinessException {

    /** PG 결제 키 누락 에러 코드로 예외를 생성한다. */
    public PaymentPgKeyMissingException() {
        super(ErrorCode.PAYMENT_PG_KEY_MISSING);
    }
}
