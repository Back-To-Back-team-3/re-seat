package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueRegistrationFailedException extends BusinessException {

    public QueueRegistrationFailedException() {
        super(ErrorCode.QUEUE_REGISTRATION_FAILED);
    }

    public QueueRegistrationFailedException(String message) {
        super(ErrorCode.QUEUE_REGISTRATION_FAILED, message);
    }
}
