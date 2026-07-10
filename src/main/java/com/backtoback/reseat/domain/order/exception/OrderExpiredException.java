package com.backtoback.reseat.domain.order.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class OrderExpiredException extends BusinessException {

    public OrderExpiredException() {
        super(ErrorCode.ORDER_EXPIRED);
    }
}
