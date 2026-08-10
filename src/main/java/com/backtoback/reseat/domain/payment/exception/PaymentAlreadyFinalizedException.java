package com.backtoback.reseat.domain.payment.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 이미 최종 상태로 처리된 결제에 다시 완료 또는 실패 처리를 요청했을 때 발생한다.
 *
 * <p>READY 상태가 아닌 결제에 complete/fail 콜백이 들어오는 경우 사용한다.</p>
 */
public class PaymentAlreadyFinalizedException extends BusinessException {

    /**
     * 이미 최종 처리된 결제 에러 코드로 예외를 생성한다.
     */
    public PaymentAlreadyFinalizedException() {
        super(ErrorCode.PAYMENT_ALREADY_FINALIZED);
    }
}
