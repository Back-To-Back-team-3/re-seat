package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentCancelFailedException extends BusinessException {

    public PaymentCancelFailedException() {
        super(ErrorCode.PAYMENT_CANCEL_FAILED);
    }

    public PaymentCancelFailedException(String message) {
        super(ErrorCode.PAYMENT_CANCEL_FAILED, message);
    }
}
