package com.backtoback.reseat.domain.game.exception;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.Getter;

/**
 * 허용되지 않은 예매 상태 전이를 시도한 경우 발생하는 예외.
 *
 * <p>발생 경로
 * - Game.openBooking()/closeBooking()/cancelGame() — 도메인 가드
 * - 관리자 상태 전이 서비스 — 조건부 UPDATE가 0건을 반환한 경합 상황
 *
 * <p>ErrorCode: INVALID_BOOKING_STATUS_TRANSITION (409)
 */
@Getter
public class InvalidBookingStatusTransitionException extends BusinessException {

    private final BookingStatus current;
    private final BookingStatus target;

    public InvalidBookingStatusTransitionException(BookingStatus current, BookingStatus target) {
        super(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION);
        this.current = current;
        this.target = target;
    }
}
