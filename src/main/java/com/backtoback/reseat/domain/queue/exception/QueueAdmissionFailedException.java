package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueAdmissionFailedException extends BusinessException {

    public QueueAdmissionFailedException() {
        super(ErrorCode.QUEUE_ADMISSION_FAILED);
    }

    public QueueAdmissionFailedException(String message) {
        super(ErrorCode.QUEUE_ADMISSION_FAILED, message);
    }
}
