package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 사용자를 Redis 대기열에 정상적으로 등록했는지 확인할 수 없을 때 발생하는 예외
 */
public class QueueRegistrationFailedException extends BusinessException {

    public QueueRegistrationFailedException() {
        super(ErrorCode.QUEUE_REGISTRATION_FAILED);
    }

    public QueueRegistrationFailedException(String message) {
        super(ErrorCode.QUEUE_REGISTRATION_FAILED, message);
    }
}
