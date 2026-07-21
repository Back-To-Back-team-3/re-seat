package com.backtoback.reseat.domain.seatinventory.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 허용되지 않은 좌석 상태 전이 시도 시 던지는 예외.
 * <p>
 * 사용 위치: GameSeat — 허용되지 않은 상태에서 hold/release/sell 호출 시
 * - hold()  : AVAILABLE이 아닌 좌석 선점 시도
 * - release(): HELD가 아닌 좌석 해제 시도
 * - sell()  : HELD가 아닌 좌석 판매 시도
 * <p>
 * 정상 흐름에서는 서비스 계층의 사전 검증(SEAT_ALREADY_HELD 등)이 선행하므로,
 * 이 예외가 실제로 발생하면 동시성 레이스 또는 로직 버그 신호다.
 * <p>
 * ErrorCode: INVALID_STATE_TRANSITION (409)
 */
public class InvalidStateTransitionException extends BusinessException {
    public InvalidStateTransitionException() {
        super(ErrorCode.INVALID_STATE_TRANSITION);
    }
}
