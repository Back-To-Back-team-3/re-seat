package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 현재 결제 시도의 활성 Idempotency-Key가 아닌 키로 종결을 요청할 때 발생한다.
 */
public class IdempotencyKeyUnavailableException extends BusinessException {

	/**
	 * 사용할 수 없는 멱등키 에러 코드로 예외를 생성한다.
	 */
	public IdempotencyKeyUnavailableException() {
		super(ErrorCode.IDEMPOTENCY_KEY_UNAVAILABLE);
	}
}
