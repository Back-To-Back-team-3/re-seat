package com.backtoback.reseat.domain.seatinventory.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;
import lombok.Getter;

/**
 * 이미 좌석 재고가 오픈된 경기에 재고 오픈을 재요청한 경우 발생하는 오류이다.
 */
@Getter
public class SeatInventoryAlreadyOpenedException extends BusinessException {

    private final Long gameId;

    public SeatInventoryAlreadyOpenedException(Long gameId) {
        super(ErrorCode.SEAT_INVENTORY_ALREADY_OPENED);
        this.gameId = gameId;
    }
}
