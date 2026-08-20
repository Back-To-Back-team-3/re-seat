package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class DeleteUserException extends BusinessException {

    public DeleteUserException() { super(ErrorCode.UNAUTHORIZED);
    }
}
