package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class IdempotencyKeyConflictException extends BusinessException {

    public IdempotencyKeyConflictException() {
        super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }
}
