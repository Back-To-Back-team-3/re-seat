package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketNotFoundException extends BusinessException {

    public TicketNotFoundException() {
        super(ErrorCode.TICKET_NOT_FOUND);
    }
}
