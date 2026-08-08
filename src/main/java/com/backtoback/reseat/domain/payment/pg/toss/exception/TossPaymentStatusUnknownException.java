package com.backtoback.reseat.domain.payment.pg.toss.exception;

/**
 * Toss API 호출 후 재조회로도 최종 결제 상태를 확정할 수 없을 때 발생
 *
 * <p>API 응답용 비즈니스 예외가 아니라 호출 서비스가 후속 처리 정책을 결정하도록 전달하는 내부 연동 예외이므로,
 * BusinessException을 상속받지 않습니다.</p>
 */
public class TossPaymentStatusUnknownException extends RuntimeException {

    /**
     * 실패한 작업과 최초 API 호출 실패 원인을 포함해 예외를 생성한다.
     */
    public TossPaymentStatusUnknownException(String operation, Throwable cause) {
        super("토스 결제 " + operation + " 후 상태를 확인할 수 없습니다.", cause);
    }
}
