package com.backtoback.reseat.domain.game.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 예매 가능한 상태가 아닌 경기의 대기열 진입을 요청한 경우 발생하는 예외
 */
public class BookingNotOpenException extends BusinessException {

    public BookingNotOpenException() {
        super(ErrorCode.BOOKING_NOT_OPEN);
    }
}
