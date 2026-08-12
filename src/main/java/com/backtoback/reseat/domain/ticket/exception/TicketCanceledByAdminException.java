package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketCanceledByAdminException extends BusinessException {

    public TicketCanceledByAdminException() {
        super(ErrorCode.TICKET_ALREADY_CANCELED);
    }
}
