package com.backtoback.reseat.domain.order.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class OrderAccessDeniedException extends BusinessException {

    public OrderAccessDeniedException() {
        super(ErrorCode.FORBIDDEN, "해당 주문에 접근할 권한이 없습니다.");
    }
}
