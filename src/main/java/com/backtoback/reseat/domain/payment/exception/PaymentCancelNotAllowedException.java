package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 결제 취소를 요청할 수 없는 결제 상태일 때 발생한다.
 *
 * <p>승인되지 않은 결제, 이미 취소된 결제, PG 결제 키가 없는 결제에 사용한다.</p>
 */
public class PaymentCancelNotAllowedException extends BusinessException {

    /** 결제 취소 불가 에러 코드로 예외를 생성한다. */
    public PaymentCancelNotAllowedException() {
        super(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
    }

    /** 결제 취소 불가 에러 코드와 상세 메시지로 예외를 생성한다. */
    public PaymentCancelNotAllowedException(String message) {
        super(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED, message);
    }
}
