package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class PreReservationExpiredException extends BusinessException {

    public PreReservationExpiredException() {
        super(ErrorCode.PRE_RESERVATION_EXPIRED);
    }
}
