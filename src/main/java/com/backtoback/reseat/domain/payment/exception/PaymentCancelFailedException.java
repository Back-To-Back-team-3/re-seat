package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Toss 결제 취소 API 호출 또는 취소 응답 검증에 실패했을 때 발생한다.
 * <p>외부 PG 취소 결과를 확정할 수 없는 상태이므로 외부 연동 실패로 응답한다.</p>
 */
public class PaymentCancelFailedException extends BusinessException {

    /**
     * 결제 취소 실패 에러 코드로 예외를 생성한다.
     */
    public PaymentCancelFailedException() {
        super(ErrorCode.PAYMENT_CANCEL_FAILED);
    }

    /**
     * 결제 취소 실패 에러 코드와 상세 메시지로 예외를 생성한다.
     */
    public PaymentCancelFailedException(String message) {
        super(ErrorCode.PAYMENT_CANCEL_FAILED, message);
    }
}
