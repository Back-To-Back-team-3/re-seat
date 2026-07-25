package com.backtoback.reseat.domain.reservation.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 좌석 분산 락 획득에 실패했을 때 발생하는 예외.
 *
 * <p>동일 좌석에 대한 경쟁 요청에서 락 타임아웃이 발생하거나 스레드 인터럽트로 락을 확보하지 못한 경우에 던진다.</p>
 * <p>에러 코드: LOCK_FAILED 409 Conflict
 * <p>로그 레벨: WARN — 시스템 장애가 아닌 경쟁에서 밀려난 비즈니스 거부 상황이다.
 */
public class LockFailedException extends BusinessException {

    public LockFailedException() {
        super(ErrorCode.LOCK_FAILED);
    }

    /**
     * 예외를 내부 디버그용 상세 메시지와 함께 생성한다.
     * @param message 내부 로그용 메시지
     */
    public LockFailedException(String message) {
        super(ErrorCode.LOCK_FAILED, message);
    }
}
