package com.backtoback.reseat.domain.seatinventory.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.Getter;

/**
 * 좌석 재고가 아직 오픈되지 않은 경기를 조회하려 할 때 발생하는 오류이다.
 */
@Getter
public class SeatInventoryNotOpenedException extends BusinessException {

    private final Long gameId;

    public SeatInventoryNotOpenedException(Long gameId) {
        super(ErrorCode.SEAT_INVENTORY_NOT_OPENED);
        this.gameId = gameId;
    }
}
