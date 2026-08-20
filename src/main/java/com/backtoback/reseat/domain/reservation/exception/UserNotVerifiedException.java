package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 본인 인증을 완료하지 않은 사용자가 좌석 선점을 시도한 경우 발생한다.
 * <p>JWT 인증은 통과한 상태이므로 401이 아닌 403으로 응답한다.
 */
public class UserNotVerifiedException extends BusinessException {

    public UserNotVerifiedException() {
        super(ErrorCode.USER_NOT_VERIFIED);
    }
}
