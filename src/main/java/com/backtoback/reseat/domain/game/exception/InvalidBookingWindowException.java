package com.backtoback.reseat.domain.game.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 예매 오픈/마감 시각과 경기 일시의 순서가 올바르지 않을 때 발생하는 예외.
 */
public class InvalidBookingWindowException extends BusinessException {

    public InvalidBookingWindowException(String detailMessage) {
        super(ErrorCode.INVALID_REQUEST, detailMessage);
    }
}
