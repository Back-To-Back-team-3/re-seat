package com.backtoback.reseat.domain.verification.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class VerificationException extends BusinessException {
	public VerificationException(ErrorCode errorCode) {
		super(errorCode);
	}
}
