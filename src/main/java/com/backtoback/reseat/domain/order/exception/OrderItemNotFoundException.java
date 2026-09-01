package com.backtoback.reseat.domain.order.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 주문 항목을 찾을 수 없을 때 발생하는 예외.
 */
public class OrderItemNotFoundException extends BusinessException {

    public OrderItemNotFoundException() {
        super(ErrorCode.ORDER_ITEM_NOT_FOUND);
    }
}
