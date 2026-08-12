package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 결제 ID로 결제 내역을 찾을 수 없을 때 발생한다.
 * <p>결제 조회, 승인, 실패, 취소 처리의 공통 조회 단계에서 사용한다.</p>
 */
public class PaymentNotFoundException extends BusinessException {

	/**
	 * 결제 없음 에러 코드로 예외를 생성한다.
	 */
	public PaymentNotFoundException() {
		super(ErrorCode.PAYMENT_NOT_FOUND);
	}
}
