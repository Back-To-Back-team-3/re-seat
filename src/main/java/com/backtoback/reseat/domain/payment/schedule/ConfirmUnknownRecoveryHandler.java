package com.backtoback.reseat.domain.payment.schedule;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmUnknownRecoveryHandler implements PaymentRecoveryHandler {

    private static final String RECOVERY_CANCEL_REASON = "승인 상태 불명확 결제 자동 환불";

    private final TossPaymentClient tossPaymentClient;

    /** 승인 결과를 확인할 수 없었던 결제를 Toss에서 재조회하고 필요하면 전액 환불한다. */
    @Override
    public PaymentRecoveryResult recover(PaymentRecoveryTask task) {
        String paymentKey = task.getPayment().getPgPaymentKey();

        try {
            TossPaymentResponse paymentResponse = tossPaymentClient.getPayment(paymentKey);
            if (paymentResponse.isApproved()) {
                return cancelApprovedPayment(paymentKey);
            }
            if (!paymentResponse.isConfirmFailureStatus()) {
                log
                    .warn(
                        "토스 결제 최종 상태 확인 불가 (taskId={}, paymentId={}, tossStatus={})",
                        task.getId(),
                        task.getPayment().getId(),
                        paymentResponse.getStatus()
                    );
                return PaymentRecoveryResult.retry("토스 결제가 아직 최종 상태가 아닙니다.");
            }
            return PaymentRecoveryResult.success();
        } catch (RuntimeException exception) {
            log.warn("결제 승인 복구 PG 요청 실패 (taskId={}, paymentId={})", task.getId(), task.getPayment().getId(), exception);
            return PaymentRecoveryResult.retry("토스 결제 조회 또는 자동 환불 요청에 실패했습니다.");
        }
    }

    /** Toss에서 승인된 결제를 전액 취소하고 완료 여부를 반환한다. */
    private PaymentRecoveryResult cancelApprovedPayment(String paymentKey) {
        TossPaymentResponse cancelResponse = tossPaymentClient.cancel(paymentKey, RECOVERY_CANCEL_REASON);
        if (!cancelResponse.isCancelCompleted()) {
            return PaymentRecoveryResult.retry("토스 결제 자동 환불 상태를 확인할 수 없습니다.");
        }
        return PaymentRecoveryResult.success();
    }

    @Override
    public PaymentRecoveryType getType() {
        return PaymentRecoveryType.CONFIRM_UNKNOWN;
    }
}
