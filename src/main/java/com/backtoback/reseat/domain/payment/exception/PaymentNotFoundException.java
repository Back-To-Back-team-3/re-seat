package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentNotFoundException extends BusinessException {

    public PaymentNotFoundException() {
        super(ErrorCode.PAYMENT_NOT_FOUND);
    }
}
