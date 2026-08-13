package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PaymentTicketIssuanceException extends BusinessException {

    public PaymentTicketIssuanceException() {
        super(ErrorCode.PAYMENT_TICKET_ISSUANCE_FAILED);
    }

    public PaymentTicketIssuanceException(Throwable cause) {
        super(ErrorCode.PAYMENT_TICKET_ISSUANCE_FAILED, cause);
    }
}
