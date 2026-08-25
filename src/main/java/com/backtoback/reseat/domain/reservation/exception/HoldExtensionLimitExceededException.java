package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 재선점 시 Queue-Token의 최초 좌석 탐색 완료 시각 기준 HOLD 상한을 초과한 경우 발생하는 예외.
 * <p>불변식: 재선점 시각 + payment_deadline(8분) ≤ 최초 선점 시각 + 18분.
 * 사용자에게는 대기열 재진입(토큰 재발급)을 안내해야 한다.
 */
public class HoldExtensionLimitExceededException extends BusinessException {

    public HoldExtensionLimitExceededException() {
        super(ErrorCode.HOLD_EXTENSION_LIMIT_EXCEEDED);
    }
}
