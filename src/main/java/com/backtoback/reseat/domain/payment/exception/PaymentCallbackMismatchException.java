package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Toss 콜백 또는 리다이렉트로 전달된 결제 정보가 저장된 결제 정보와 다를 때 발생한다.
 * <p>orderId나 amount가 일치하지 않는 경우 결제 정보 변조 가능성이 있으므로 처리를 중단한다.</p>
 */
public class PaymentCallbackMismatchException extends BusinessException {

	/**
	 * 결제 콜백 정보 불일치 에러 코드로 예외를 생성한다.
	 */
	public PaymentCallbackMismatchException() {
		super(ErrorCode.PAYMENT_CALLBACK_MISMATCH);
	}
}
