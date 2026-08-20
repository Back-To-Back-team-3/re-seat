package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class AdminAccessRequiredException extends BusinessException {

    public AdminAccessRequiredException() {
        super(ErrorCode.ADMIN_ACCESS_REQUIRED);
    }

    public AdminAccessRequiredException(String message) {
        super(ErrorCode.ADMIN_ACCESS_REQUIRED, message);
    }
}
