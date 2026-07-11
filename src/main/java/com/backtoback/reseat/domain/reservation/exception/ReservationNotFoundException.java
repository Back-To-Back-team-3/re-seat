package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class ReservationNotFoundException extends BusinessException {

    /**
     * 예약을 찾을 수 없을 때 발생. → 404 RESERVATION_NOT_FOUND.
     * game 도메인 GameNotFoundException 패턴 계승.
     */
    public ReservationNotFoundException(Long reservationId) {
        super(ErrorCode.RESERVATION_NOT_FOUND,
            "예약을 찾을 수 없습니다. reservationId=" + reservationId);
    }
}
