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
        // 복구 유형별 Handler를 미리 등록해 실행 시 switch 없이 알맞은 구현체를 바로 조회한다.
        this.handlers = new EnumMap<>(PaymentRecoveryType.class);
        handlers.forEach(this::registerHandler);
    }

    /** 복구 유형별 Handler를 한 개만 등록한다. */
    private void registerHandler(PaymentRecoveryHandler handler) {
        PaymentRecoveryHandler registered = handlers.putIfAbsent(handler.getType(), handler);
        if (registered != null) {
            throw new IllegalStateException("결제 복구 Handler가 중복 등록되었습니다: " + handler.getType());
        }
    }

    /** 실행 가능한 결제 복구 작업을 선점하고 유형별 Handler의 결과에 따라 상태를 전이한다. */
    @Transactional
    public void recover(Long taskId, LocalDateTime now) {
        PaymentRecoveryTask task = paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(taskId).orElse(null);
        if (task == null) {
            // 스케줄러 조회 이후 작업이 삭제됐다면 별도 처리 없이 종료한다.
            return;
        }

        // 최초 실행을 기다리는 작업은 즉시 처리할 수 있다.
        boolean pending = task.getStatus() == PaymentRecoveryStatus.PENDING;
        // 재시도 작업은 예약 시각이 지난 경우에만 처리한다.
        boolean retryDue
            = task.getStatus() == PaymentRecoveryStatus.RETRY && task.getNextRetryAt() != null
                && !task.getNextRetryAt().isAfter(now);
        if (!pending && !retryDue) {
            // 현재 실행할 수 있는 상태가 아니면 종료한다.
            return;
        }

        // 트랜잭션 종료 전까지 비관적 락을 유지하므로 같은 작업은 동시에 실행되지 않는다.
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
