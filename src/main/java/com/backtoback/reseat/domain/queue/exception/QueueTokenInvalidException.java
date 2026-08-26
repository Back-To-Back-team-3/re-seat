package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Queue-Token이 존재하지 않거나 요청한 사용자 또는 경기 정보와 일치하지 않을 때 발생하는 예외
 */
public class QueueTokenInvalidException extends BusinessException {

    public QueueTokenInvalidException() {
        super(ErrorCode.QUEUE_TOKEN_INVALID);
    }
}
