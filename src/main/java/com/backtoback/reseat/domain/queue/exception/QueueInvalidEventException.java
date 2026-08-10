package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Kafka 대기열 진입 이벤트의 필수 값이 누락되거나 올바르지 않을 때 발생하는 예외
 */
public class QueueInvalidEventException extends BusinessException {

    public QueueInvalidEventException() {
        super(ErrorCode.QUEUE_INVALID_EVENT);
    }

    public QueueInvalidEventException(String message) {
        super(ErrorCode.QUEUE_INVALID_EVENT, message);
    }
}
