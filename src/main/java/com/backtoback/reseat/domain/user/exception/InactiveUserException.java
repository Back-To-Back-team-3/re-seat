package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class InactiveUserException extends BusinessException {

    public InactiveUserException() {
        super(ErrorCode.USER_INACTIVE);
    }

    public InactiveUserException(String message) {
        super(ErrorCode.USER_INACTIVE, message);
    }
}
