package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class ReservationAccessDeniedException extends BusinessException {

    public ReservationAccessDeniedException() {
        super(ErrorCode.RESERVATION_ACCESS_DENIED);
    }
}
