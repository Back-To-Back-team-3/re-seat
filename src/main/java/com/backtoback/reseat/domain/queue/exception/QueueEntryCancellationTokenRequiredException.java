package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 입장 허용된 대기 이력을 활성 Queue-Token 없이 취소하려는 경우 발생하는 예외
 */
public class QueueEntryCancellationTokenRequiredException extends BusinessException {

    public QueueEntryCancellationTokenRequiredException() {
        super(ErrorCode.QUEUE_ENTRY_CANCELLATION_TOKEN_REQUIRED);
    }
}
