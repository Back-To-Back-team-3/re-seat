package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 대기 취소 또는 연결 종료 유예시간 만료로 사용이 취소된 Queue-Token인 경우 발생하는 예외
 */
public class QueueTokenRevokedException extends BusinessException {

    public QueueTokenRevokedException() {
        super(ErrorCode.QUEUE_TOKEN_REVOKED);
    }

    public QueueTokenRevokedException(Throwable cause) {
        super(ErrorCode.QUEUE_TOKEN_REVOKED, cause);
    }
}
