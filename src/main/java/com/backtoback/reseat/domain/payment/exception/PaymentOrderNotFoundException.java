package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentOrderNotFoundException extends BusinessException {

    public PaymentOrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
