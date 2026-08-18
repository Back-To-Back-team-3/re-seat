package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 대기열 진입 이벤트가 누락된 경우 발생하는 예외
 */
public class QueueEntryEventRequiredException extends BusinessException {

    public QueueEntryEventRequiredException() {
        super(ErrorCode.QUEUE_ENTRY_EVENT_REQUIRED);
    }
}
