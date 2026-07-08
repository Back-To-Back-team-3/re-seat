package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class InvalidOrderStatusException extends BusinessException {

    public InvalidOrderStatusException() {
        super(ErrorCode.INVALID_ORDER_STATUS);
    }
}
