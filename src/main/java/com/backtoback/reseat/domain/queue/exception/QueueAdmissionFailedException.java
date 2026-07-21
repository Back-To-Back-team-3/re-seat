package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 대기열 사용자의 자동 입장 허용 처리를 계속할 수 없을 때 발생하는 예외
 */
public class QueueAdmissionFailedException extends BusinessException {

    public QueueAdmissionFailedException() {
        super(ErrorCode.QUEUE_ADMISSION_FAILED);
    }

    public QueueAdmissionFailedException(String message) {
        super(ErrorCode.QUEUE_ADMISSION_FAILED, message);
    }
}
