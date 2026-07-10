package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class InvalidReservationStatusException extends BusinessException {

    public InvalidReservationStatusException() {
        super(ErrorCode.INVALID_RESERVATION_STATUS);
    }
}
