package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class ReservationSeatNotFoundException extends BusinessException {

    public ReservationSeatNotFoundException() {
        super(ErrorCode.RESERVATION_SEAT_NOT_FOUND);
    }
}
