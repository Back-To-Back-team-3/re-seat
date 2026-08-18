package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Redis 대기열 사용자 정보가 올바르지 않은 경우 발생하는 예외
 */
public class QueueRedisMemberInvalidException extends BusinessException {

    public QueueRedisMemberInvalidException() {
        super(ErrorCode.QUEUE_REDIS_MEMBER_INVALID);
    }

    public QueueRedisMemberInvalidException(Throwable cause) {
        super(ErrorCode.QUEUE_REDIS_MEMBER_INVALID, cause);
    }
}
