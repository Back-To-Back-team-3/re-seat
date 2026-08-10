package com.backtoback.reseat.domain.user.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class DuplicateEmailException extends BusinessException {
	public DuplicateEmailException(String message) {
		super(ErrorCode.DUPLICATE_LOGIN_ID, message);
	}
}
