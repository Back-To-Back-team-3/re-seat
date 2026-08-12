package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketAccessDeniedException extends BusinessException {

    public TicketAccessDeniedException() {
        super(ErrorCode.TICKET_ACCESS_DENIED);
    }
}
