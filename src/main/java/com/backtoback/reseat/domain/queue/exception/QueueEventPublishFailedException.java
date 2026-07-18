package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueEventPublishFailedException extends BusinessException {

    public QueueEventPublishFailedException() {
        super(ErrorCode.QUEUE_EVENT_PUBLISH_FAILED);
    }

    public QueueEventPublishFailedException(String message) {
        super(ErrorCode.QUEUE_EVENT_PUBLISH_FAILED, message);
    }
}
