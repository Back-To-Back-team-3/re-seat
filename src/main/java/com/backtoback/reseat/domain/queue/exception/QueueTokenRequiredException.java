package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 사용자에게 유효한 활성 입장 토큰이 존재하지 않을 때 발생하는 예외
 */
public class QueueTokenRequiredException extends BusinessException {

    public QueueTokenRequiredException() {
        super(ErrorCode.QUEUE_TOKEN_REQUIRED);
    }
}
