package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 이미 사용된 Idempotency-Key로 다른 결제 요청을 보냈을 때 발생한다.
 *
 * <p>같은 키는 같은 사용자와 같은 주문의 결제 요청에만 재사용할 수 있다.</p>
 */
public class IdempotencyKeyConflictException extends BusinessException {

    /** 멱등키 충돌 에러 코드로 예외를 생성한다. */
    public IdempotencyKeyConflictException() {
        super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }
}
