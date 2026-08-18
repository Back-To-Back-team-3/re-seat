package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 재진입할 수 없는 상태의 대기열 진입 이력을 재진입 처리하려는 경우 발생하는 예외
 */
public class QueueEntryReentryNotAllowedException extends BusinessException {

    public QueueEntryReentryNotAllowedException() {
        super(ErrorCode.QUEUE_ENTRY_REENTRY_NOT_ALLOWED);
    }
}
