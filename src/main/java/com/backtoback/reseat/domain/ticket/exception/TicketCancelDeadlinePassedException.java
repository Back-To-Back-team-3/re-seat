package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketCancelDeadlinePassedException extends BusinessException {

    public TicketCancelDeadlinePassedException() {
        super(ErrorCode.TICKET_CANCEL_DEADLINE_PASSED);
    }
}
