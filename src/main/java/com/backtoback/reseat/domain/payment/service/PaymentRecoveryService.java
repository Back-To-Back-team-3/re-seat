package com.backtoback.reseat.domain.payment.service;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_DELAY_MINUTES = 1L;
    private static final String RECOVERY_CANCEL_REASON = "승인 상태 불명확 결제 자동 환불";

    private final PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;
    private final TossPaymentClient tossPaymentClient;

    /**
     * 실행 가능한 승인 복구 작업을 선점해 Toss 상태 확인과 필요 시 전액 취소를 수행한다.
     */
    @Transactional
    public void recover(Long taskId, LocalDateTime now) {
        PaymentRecoveryTask task = paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(taskId)
            .orElse(null);
        if (task == null) {
            // 복구 작업이 없으므로 종료
            return;
        }

        // 복구 작업 Status가 Pending인 경우
        boolean pending = task.getStatus() == PaymentRecoveryStatus.PENDING;
        // 복구 작업 Status가 Retry인 경우 && 다음 재시도 시간을 넘어간 경우
        boolean retryDue = task.getStatus() == PaymentRecoveryStatus.RETRY
            && task.getNextRetryAt() != null
            && !task.getNextRetryAt().isAfter(now);
        if (!pending && !retryDue) {
            // 둘다 해당하지 않으면 종료
            return;
        }

        // 복구 작업 처리중으로 상태 전환
        task.startProcessing(now);
        String paymentKey = task.getPayment().getPgPaymentKey();

        try {
            TossPaymentResponse paymentResponse = tossPaymentClient.getPayment(paymentKey);

            // 로컬은 실패했지만 Toss에서 승인됐다면 실제 결제 금액이 남아 있으므로 전액 취소한다.
            if (paymentResponse.isApproved()) {
                TossPaymentResponse cancelResponse =
                    tossPaymentClient.cancel(paymentKey, RECOVERY_CANCEL_REASON);
                // 취소 완료를 확인하지 못하면 복구를 끝내지 않고 다시 조회할 수 있도록 재시도한다.
                if (!cancelResponse.isCancelCompleted()) {
                    retryOrFail(task, "토스 결제 자동 환불 상태를 확인할 수 없습니다.", now);
                    return;
                }
            } else if (!paymentResponse.isConfirmFailureStatus()) {
                // 승인 실패·만료·취소 외 상태는 아직 최종 결과를 판단할 수 없으므로 재시도한다.
                retryOrFail(task, "토스 결제가 아직 최종 상태가 아닙니다.", now);
                log.warn("토스 결제 최종 상태 확인 불가 (taskId={}, paymentId={}, tossStatus={})",
                    taskId, task.getPayment().getId(), paymentResponse.getStatus());
                return;
            }

            // 환불을 완료했거나 승인 실패·만료·기취소 상태를 확인했으므로 복구 작업을 종료한다.
            task.complete(now);
            log.info("결제 승인 복구 작업 완료 (taskId={}, paymentId={}, paymentKey={})",
                taskId, task.getPayment().getId(), paymentKey);
        } catch (RuntimeException e) {
            retryOrFail(task, "토스 결제 조회 또는 자동 환불 요청에 실패했습니다.", now);
            log.warn("결제 승인 복구 작업 실패 (taskId={}, paymentId={}, attemptCount={})",
                taskId, task.getPayment().getId(), task.getAttemptCount(), e);
        }
    }

    private void retryOrFail(PaymentRecoveryTask task, String error, LocalDateTime now) {
        if (task.getAttemptCount() >= MAX_RETRY_COUNT) {
            task.fail(error);
            log.error("결제 승인 복구 작업 최종 실패 (taskId={}, paymentId={}, retryCount={}, reason={})",
                task.getId(), task.getPayment().getId(), task.getAttemptCount(), error);
            return;
        }

        task.scheduleRetry(error, now.plusMinutes(RETRY_DELAY_MINUTES));
    }
}
