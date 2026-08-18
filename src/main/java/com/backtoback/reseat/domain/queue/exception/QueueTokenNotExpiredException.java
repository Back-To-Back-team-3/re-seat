package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 만료 시간이 지나지 않은 Queue-Token을 만료 처리하려는 경우 발생하는 예외
 */
public class QueueTokenNotExpiredException extends BusinessException {

    public QueueTokenNotExpiredException() {
        super(ErrorCode.QUEUE_TOKEN_NOT_EXPIRED);
    }
}
