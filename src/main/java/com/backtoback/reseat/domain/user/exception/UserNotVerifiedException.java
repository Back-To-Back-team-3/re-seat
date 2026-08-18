package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class UserNotVerifiedException extends BusinessException {

    public UserNotVerifiedException() {
        super(ErrorCode.USER_NOT_VERIFIED);
    }

    public UserNotVerifiedException(String message) {
        super(ErrorCode.USER_NOT_VERIFIED, message);
    }
}
