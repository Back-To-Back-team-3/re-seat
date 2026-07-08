package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class DuplicateNicknameException extends BusinessException {
    public DuplicateNicknameException(String message) {
        super(ErrorCode.DUPLICATE_LOGIN_ID, message);
    }
}
