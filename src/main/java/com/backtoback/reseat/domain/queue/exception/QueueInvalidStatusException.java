package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueInvalidStatusException extends BusinessException {

    public QueueInvalidStatusException(String message) {
        super(ErrorCode.QUEUE_INVALID_STATUS, message);
    }
}
