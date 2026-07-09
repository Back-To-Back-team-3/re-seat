package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class QueueEntryNotFoundException extends BusinessException {

    public QueueEntryNotFoundException() {
        super(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
    }
}
