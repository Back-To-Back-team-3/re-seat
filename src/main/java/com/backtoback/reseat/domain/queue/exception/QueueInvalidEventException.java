package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueInvalidEventException extends BusinessException {

    public QueueInvalidEventException() {
        super(ErrorCode.QUEUE_INVALID_EVENT);
    }

    public QueueInvalidEventException(String message) {
        super(ErrorCode.QUEUE_INVALID_EVENT, message);
    }
}
