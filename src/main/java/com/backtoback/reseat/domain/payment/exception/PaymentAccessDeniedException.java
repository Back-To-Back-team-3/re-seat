package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 요청 사용자가 결제 또는 주문의 소유자가 아닐 때 발생한다.
 *
 * <p>결제 조회, 승인, 실패, 취소 처리에서 본인 결제만 다룰 수 있도록 막는다.</p>
 */
public class PaymentAccessDeniedException extends BusinessException {

    public PaymentAccessDeniedException() {
        super(ErrorCode.PAYMENT_ACCESS_DENIED);
    }
}
