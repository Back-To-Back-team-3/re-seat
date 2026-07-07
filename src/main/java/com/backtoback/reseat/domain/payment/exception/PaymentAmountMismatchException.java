package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentAmountMismatchException extends BusinessException {

    public PaymentAmountMismatchException() {
        super(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }
}
