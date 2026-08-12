package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 타인의 예약에 접근을 시도할 때 발생하는 예외.
 * <p>
 * 소유자 검증 실패 시 {@code RESERVATION_ACCESS_DENIED}(403)으로 응답한다.
 * 존재 여부는 인정하되 접근만 거부한다. (API 명세서 0.4, 5.3)
 * <p>
 * 에러 응답 변환은 {@code GlobalExceptionHandler}의
 * {@code BusinessException} 핸들러가 담당한다.
 */
public class ReservationAccessDeniedException extends BusinessException {

	public ReservationAccessDeniedException() {
		super(ErrorCode.RESERVATION_ACCESS_DENIED);
	}
}
