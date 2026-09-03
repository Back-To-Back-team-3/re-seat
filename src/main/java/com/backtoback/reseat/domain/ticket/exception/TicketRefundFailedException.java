package com.backtoback.reseat.domain.ticket.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class TicketRefundFailedException extends BusinessException {

    public TicketRefundFailedException() {
        super(ErrorCode.TICKET_REFUND_FAILED_RETRY_REQUIRED);
    }
}
