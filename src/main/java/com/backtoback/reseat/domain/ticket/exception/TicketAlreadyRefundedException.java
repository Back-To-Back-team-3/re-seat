package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketAlreadyRefundedException extends BusinessException {

    public TicketAlreadyRefundedException() {
        super(ErrorCode.TICKET_ALREADY_REFUNDED);
    }
}
