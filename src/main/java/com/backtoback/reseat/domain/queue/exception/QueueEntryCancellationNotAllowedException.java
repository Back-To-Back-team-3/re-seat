package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 취소할 수 없는 상태의 대기열 진입 이력을 취소하려는 경우 발생하는 예외
 */
public class QueueEntryCancellationNotAllowedException extends BusinessException {

    public QueueEntryCancellationNotAllowedException() {
        super(ErrorCode.QUEUE_ENTRY_CANCELLATION_NOT_ALLOWED);
    }
}
