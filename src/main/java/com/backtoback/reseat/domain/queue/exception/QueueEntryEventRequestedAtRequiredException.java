package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 대기열 진입 요청 시간이 누락된 경우 발생하는 예외
 */
public class QueueEntryEventRequestedAtRequiredException extends BusinessException {

    public QueueEntryEventRequestedAtRequiredException() {
        super(ErrorCode.QUEUE_ENTRY_EVENT_REQUESTED_AT_REQUIRED);
    }
}
