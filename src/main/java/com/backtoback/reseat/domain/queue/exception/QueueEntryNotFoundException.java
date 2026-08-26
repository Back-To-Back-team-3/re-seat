package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 사용자의 대기열 등록 정보 또는 DB 진입 이력을 찾을 수 없을 때 발생하는 예외
 */
public class QueueEntryNotFoundException extends BusinessException {

    public QueueEntryNotFoundException() {
        super(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
    }
}
