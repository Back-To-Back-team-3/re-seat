package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 예약·좌석의 현재 상태가 요청한 전이를 허용하지 않을 때 던지는 예외.
 * <p>
 * 사용 위치
 * - Reservation — HOLDING이 아닌 상태에서 confirm/cancel/expire 호출 시
 * - GameSeat — 허용되지 않은 상태에서 hold/release/sell 호출 시
 * <p>
 * 정상 흐름에서는 서비스 계층의 사전 검증(SEAT_ALREADY_HELD 등)이 선행하므로,
 * 이 예외가 실제로 발생하면 동시성 레이스 또는 로직 버그 신호다.
 * <p>
 * ErrorCode: INVALID_RESERVATION_STATUS (409)
 */
public class InvalidReservationStatusException extends BusinessException {

	public InvalidReservationStatusException() {
		super(ErrorCode.INVALID_RESERVATION_STATUS);
	}
}
