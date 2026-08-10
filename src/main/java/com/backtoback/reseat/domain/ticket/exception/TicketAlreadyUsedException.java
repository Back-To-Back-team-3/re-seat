package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketAlreadyUsedException extends BusinessException {

    public TicketAlreadyUsedException() {
        super(ErrorCode.TICKET_ALREADY_USED);
    }
}
