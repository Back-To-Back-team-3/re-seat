package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Redis 대기열 Key가 올바르지 않은 경우 발생하는 예외
 */
public class QueueRedisKeyInvalidException extends BusinessException {

    public QueueRedisKeyInvalidException() {
        super(ErrorCode.QUEUE_REDIS_KEY_INVALID);
    }

    public QueueRedisKeyInvalidException(Throwable cause) {
        super(ErrorCode.QUEUE_REDIS_KEY_INVALID, cause);
    }
}
