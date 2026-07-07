package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentAlreadyFinalizedException extends BusinessException {

    public PaymentAlreadyFinalizedException() {
        super(ErrorCode.PAYMENT_ALREADY_FINALIZED);
    }
}
