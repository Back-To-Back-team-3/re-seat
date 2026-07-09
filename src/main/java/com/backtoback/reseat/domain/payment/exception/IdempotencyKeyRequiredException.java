package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class IdempotencyKeyRequiredException extends BusinessException {

    public IdempotencyKeyRequiredException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }
}
