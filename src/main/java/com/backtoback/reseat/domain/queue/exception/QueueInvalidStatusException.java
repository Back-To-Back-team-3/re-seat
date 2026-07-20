package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 현재 대기열 상태에서 요청한 상태 전이를 수행할 수 없을 때 발생하는 예외
 */
public class QueueInvalidStatusException extends BusinessException {

    public QueueInvalidStatusException(String message) {
        super(ErrorCode.QUEUE_INVALID_STATUS, message);
    }
}
