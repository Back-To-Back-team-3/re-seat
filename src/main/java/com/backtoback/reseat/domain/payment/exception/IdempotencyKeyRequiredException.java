package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 결제 요청에 Idempotency-Key 헤더가 없거나 비어 있을 때 발생한다.
 * <p>결제 생성과 승인·실패 콜백에는 현재 결제 시도를 식별할 멱등키가 필요하다.</p>
 */
public class IdempotencyKeyRequiredException extends BusinessException {

    /**
     * 멱등키 필수 에러 코드로 예외를 생성한다.
     */
    public IdempotencyKeyRequiredException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }
}
