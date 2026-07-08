package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentCallbackMismatchException extends BusinessException {

    public PaymentCallbackMismatchException() {
        super(ErrorCode.PAYMENT_CALLBACK_MISMATCH);
    }
}
