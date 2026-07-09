package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentCancelNotAllowedException extends BusinessException {

    public PaymentCancelNotAllowedException() {
        super(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
    }

    public PaymentCancelNotAllowedException(String message) {
        super(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED, message);
    }
}
