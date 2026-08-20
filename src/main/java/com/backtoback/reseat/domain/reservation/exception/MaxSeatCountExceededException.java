package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 누적 보유 좌석 수(HOLDING 예약 + 유효 티켓)와 요청 좌석 수의 합이
 * 1인당 최대 보유 좌석 수(SeatCountHolicy.MAX_SEAT_COUNT_PER_GAME)를 초과할 때 던지는 예외.
 * <p>
 * ErrorCode: MAX_SEAT_COUNT_EXCEEDED (400)
 */
public class MaxSeatCountExceededException extends BusinessException {

    public MaxSeatCountExceededException() {
        super(ErrorCode.MAX_SEAT_COUNT_EXCEEDED);
    }
}
