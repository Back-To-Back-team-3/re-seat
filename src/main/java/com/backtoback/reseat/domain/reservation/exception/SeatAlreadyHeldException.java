package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 이미 선점(HELD)·판매(SOLD)된 좌석을 다시 선점하려 할 때 발생. → 409 SEAT_ALREADY_HELD.
 * <p>두 트랜잭션이 동시에 AVAILABLE을 확인하면 둘 다 이 검증을 통과한다. → C-4에서 분산락으로 방어.
 */
public class SeatAlreadyHeldException extends BusinessException {

    public SeatAlreadyHeldException(Long gameSeatId) {
        super(ErrorCode.SEAT_ALREADY_HELD, "이미 선점된 좌석입니다. gameSeatId=" + gameSeatId);
    }
}
