package com.backtoback.reseat.domain.payment.schedule;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentRecoveryService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_DELAY_MINUTES = 1L;
    private final PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;
    private final Map<PaymentRecoveryType, PaymentRecoveryHandler> handlers;

    public PaymentRecoveryService(
        PaymentRecoveryTaskRepository paymentRecoveryTaskRepository,
        List<PaymentRecoveryHandler> handlers
    ) {
        this.paymentRecoveryTaskRepository = paymentRecoveryTaskRepository;
        this.handlers = new EnumMap<>(PaymentRecoveryType.class);
        // 복구 유형을 키로 사용하는 전용 Map에 각 처리기를 등록해 handlers.get(PaymentRecoveryType.getType())으로 알맞은 구현체를 조회한다.
        handlers.forEach(handler -> this.handlers.put(handler.getType(), handler));
    }

    /**
     * 실행 가능한 승인 복구 작업을 선점해 Toss 상태 확인과 필요 시 전액 취소를 수행한다.
     */
    @Transactional
    public void recover(Long taskId, LocalDateTime now) {
        PaymentRecoveryTask task = paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(taskId).orElse(null);
        if (task == null) {
            // 복구 작업이 없으므로 종료
            return;
        }

        // 복구 작업 Status가 Pending인 경우
        boolean pending = task.getStatus() == PaymentRecoveryStatus.PENDING;
        // 복구 작업 Status가 Retry인 경우 && 다음 재시도 시간을 넘어간 경우
        boolean retryDue
            = task.getStatus() == PaymentRecoveryStatus.RETRY && task.getNextRetryAt() != null
                && !task.getNextRetryAt().isAfter(now);
        if (!pending && !retryDue) {
            // 둘다 해당하지 않으면 종료
            return;
        }

        task.startProcessing(now);
        PaymentRecoveryHandler handler = handlers.get(task.getType());
        if (handler == null) {
            task.fail("지원하지 않는 결제 복구 유형입니다: " + task.getType());
            return;
        }

        try {
            PaymentRecoveryResult result = handler.recover(task);
            if (!result.successful()) {
                retryOrFail(task, result.error(), now);
                return;
            }

            task.complete(now);
            log
                .info(
                    "결제 복구 작업 완료 (taskId={}, paymentId={}, type={})",
                    taskId,
                    task.getPayment().getId(),
                    task.getType()
                );
        } catch (RuntimeException exception) {
            retryOrFail(task, "결제 복구 처리 중 예기치 않은 오류가 발생했습니다.", now);
            log
                .warn(
                    "결제 복구 작업 실패 (taskId={}, paymentId={}, type={}, attemptCount={})",
                    taskId,
                    task.getPayment().getId(),
                    task.getType(),
                    task.getAttemptCount(),
                    exception
                );
        }
    }

    private void retryOrFail(PaymentRecoveryTask task, String error, LocalDateTime now) {
        if (task.getAttemptCount() >= MAX_RETRY_COUNT) {
            task.fail(error);
            log
                .error(
                    "결제 복구 작업 최종 실패 (taskId={}, paymentId={}, type={}, retryCount={}, reason={})",
                    task.getId(),
                    task.getPayment().getId(),
                    task.getType(),
                    task.getAttemptCount(),
                    error
                );
            return;
        }

        task.scheduleRetry(error, now.plusMinutes(RETRY_DELAY_MINUTES));
    }
}
