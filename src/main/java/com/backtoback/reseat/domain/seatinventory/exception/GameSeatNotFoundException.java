package com.backtoback.reseat.domain.seatinventory.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 경기 좌석을 찾을 수 없을 때 발생하는 예외.
 */
public class GameSeatNotFoundException extends BusinessException {

    public GameSeatNotFoundException() {
        super(ErrorCode.GAME_SEAT_NOT_FOUND);
    }
}
