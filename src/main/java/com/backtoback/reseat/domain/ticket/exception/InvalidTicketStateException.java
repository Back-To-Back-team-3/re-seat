package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class InvalidTicketStateException extends BusinessException {

    public InvalidTicketStateException() {
        super(ErrorCode.INVALID_STATE_TRANSITION);
    }

    public InvalidTicketStateException(String detailMessage) {
        super(ErrorCode.INVALID_STATE_TRANSITION, detailMessage);
    }
}
