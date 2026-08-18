package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 대기열 진입 이벤트의 경기 ID가 올바르지 않은 경우 발생하는 예외
 */
public class QueueEntryEventGameIdInvalidException extends BusinessException {

    public QueueEntryEventGameIdInvalidException() {
        super(ErrorCode.QUEUE_ENTRY_EVENT_GAME_ID_INVALID);
    }
}
