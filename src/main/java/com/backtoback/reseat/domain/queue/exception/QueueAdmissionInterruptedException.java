package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 대기열 자동 입장 처리 중 스레드가 중단된 경우 발생하는 예외
 */
public class QueueAdmissionInterruptedException extends BusinessException {

    public QueueAdmissionInterruptedException() {
        super(ErrorCode.QUEUE_ADMISSION_INTERRUPTED);
    }

    public QueueAdmissionInterruptedException(Throwable cause) {
        super(ErrorCode.QUEUE_ADMISSION_INTERRUPTED, cause);
    }
}
