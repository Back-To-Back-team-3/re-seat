package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketRefundInProgressException extends BusinessException {

    public TicketRefundInProgressException() {
        super(ErrorCode.TICKET_REFUND_IN_PROGRESS);
    }
}
