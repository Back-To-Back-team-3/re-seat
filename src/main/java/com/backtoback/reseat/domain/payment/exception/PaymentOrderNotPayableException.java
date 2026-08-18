package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 결제 대상 주문이 결제 가능한 상태가 아닐 때 발생한다.
 * <p>현재는 주문 상태가 CREATED가 아닌 경우 사용한다.</p>
 */
public class PaymentOrderNotPayableException extends BusinessException {

    /**
     * 결제 불가 주문 상태 에러 코드로 예외를 생성한다.
     */
    public PaymentOrderNotPayableException() {
        super(ErrorCode.INVALID_ORDER_STATUS);
    }
}
