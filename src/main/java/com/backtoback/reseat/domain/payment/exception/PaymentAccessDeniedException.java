package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentAccessDeniedException extends BusinessException {

    public PaymentAccessDeniedException() {
        super(ErrorCode.PAYMENT_ACCESS_DENIED);
    }
}
