package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 결제 생성 요청의 주문 ID로 주문을 찾을 수 없을 때 발생한다.
 *
 * <p>결제는 주문을 기준으로 생성되므로 주문이 없으면 결제 요청을 진행할 수 없다.</p>
 */
public class PaymentOrderNotFoundException extends BusinessException {

    /** 주문 없음 에러 코드로 결제 주문 예외를 생성한다. */
    public PaymentOrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
