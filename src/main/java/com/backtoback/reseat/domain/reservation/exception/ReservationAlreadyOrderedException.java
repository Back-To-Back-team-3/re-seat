package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class ReservationAlreadyOrderedException extends BusinessException {

    public ReservationAlreadyOrderedException() {
        super(ErrorCode.RESERVATION_ALREADY_ORDERED);
    }
}
