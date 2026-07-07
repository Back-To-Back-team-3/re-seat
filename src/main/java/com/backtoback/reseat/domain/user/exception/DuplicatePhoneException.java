package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class DuplicatePhoneException extends BusinessException {
    public DuplicatePhoneException(String message) {
        super(ErrorCode.DUPLICATE_LOGIN_ID, message);
    }
}
