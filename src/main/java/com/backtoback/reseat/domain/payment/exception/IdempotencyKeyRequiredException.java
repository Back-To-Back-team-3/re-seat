package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 결제 요청에 Idempotency-Key 헤더가 없거나 비어 있을 때 발생한다.
 *
 * <p>결제 요청의 중복 승인 방지를 위해 모든 결제 생성 요청에는 멱등성 키가 필요하다.</p>
 */
public class IdempotencyKeyRequiredException extends BusinessException {

    /** 멱등키 필수 에러 코드로 예외를 생성한다. */
    public IdempotencyKeyRequiredException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }
}
