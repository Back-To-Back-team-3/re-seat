package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentLockFailedException extends BusinessException {

    /**
     * 결제 생성 락 획득 실패 에러 코드로 예외를 생성한다.
     */
    public PaymentLockFailedException() {
        super(ErrorCode.LOCK_FAILED);
    }
}
