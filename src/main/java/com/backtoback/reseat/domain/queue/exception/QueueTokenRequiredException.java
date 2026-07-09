package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueTokenRequiredException extends BusinessException {

    public QueueTokenRequiredException() {
        super(ErrorCode.QUEUE_TOKEN_REQUIRED);
    }
}
