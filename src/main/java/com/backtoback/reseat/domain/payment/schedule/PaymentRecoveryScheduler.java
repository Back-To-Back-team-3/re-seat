package com.backtoback.reseat.domain.payment.schedule;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private static final int RECOVERY_BATCH_SIZE = 20;

    private final PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;
    private final PaymentRecoveryService paymentRecoveryService;

    /**
     * 승인 상태가 불명확한 결제 복구 작업을 주기적으로 실행한다.
     */
    @Scheduled(
        fixedDelay = 30_000,
        initialDelay = 30_000
    )
    public void recoverUnknownConfirmPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> taskIds
            = paymentRecoveryTaskRepository
                .findRecoverableTaskIds(
                    PaymentRecoveryStatus.PENDING,
                    PaymentRecoveryStatus.RETRY,
                    now,
                    PageRequest.of(0, RECOVERY_BATCH_SIZE)
                );

        for (Long taskId : taskIds) {
            try {
                paymentRecoveryService.recover(taskId, now);
            } catch (RuntimeException e) {
                log.error("결제 승인 복구 작업 실행 중 예기치 않은 오류 (taskId={})", taskId, e);
            }
        }
    }
}
