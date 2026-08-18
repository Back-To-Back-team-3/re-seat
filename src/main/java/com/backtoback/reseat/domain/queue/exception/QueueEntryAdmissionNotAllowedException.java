package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 입장 허용할 수 없는 상태의 대기열 진입 이력을 입장 처리하려는 경우 발생하는 예외
 */
public class QueueEntryAdmissionNotAllowedException extends BusinessException {

    public QueueEntryAdmissionNotAllowedException() {
        super(ErrorCode.QUEUE_ENTRY_ADMISSION_NOT_ALLOWED);
    }
}
