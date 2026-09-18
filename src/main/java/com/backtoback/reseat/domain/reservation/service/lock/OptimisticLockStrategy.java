package com.backtoback.reseat.domain.reservation.service.lock;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.reservation.exception.LockFailedException;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code @Version} 기반 낙관적 락 전략.
 * <p>
 * 락을 사전 획득하지 않는 전략이므로, {@link SeatLockStrategy}가 명시하는
 * "gameSeatIds 오름차순 정렬 후 순차 획득·역순 해제" 규칙은 이 구현체에는 적용되지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "reservation",
    name = "lock-strategy",
    havingValue = "distributed",
    matchIfMissing = true
)
@RequiredArgsConstructor
public class OptimisticLockStrategy implements SeatLockStrategy {

    // 재시도 정책: 최초 시도 1회 + 재시도 2회(총 3회), 지수 백오프(50ms → 100ms) — 재시도 폭풍을 완화한다.
    private static final int MAX_RETRY = 3;
    private static final long BASE_BACKOFF_MS = 50L;
    private static final String RETRY_METRIC_NAME = "optimistic_lock_retry_total";

    private final MeterRegistry meterRegistry;

    /**
     * @throws LockFailedException 재시도 상한(3회) 초과 또는 스레드 인터럽트 시
     */
    @Override
    public <T> T executeWithLocks(List<Long> gameSeatIds, Supplier<T> action) {
        int attempt = 0;
        while (true) {
            try {
                return action.get();
            } catch (ConcurrencyFailureException e) {
                attempt++;

                if (attempt >= MAX_RETRY) {
                    log.warn("좌석 낙관적 락 재시도 상한 초과 - gameSeatIds: {}, 원인: {}", gameSeatIds, e.getClass().getSimpleName());
                    throw new LockFailedException();
                }

                meterRegistry.counter(RETRY_METRIC_NAME).increment();
                sleepWithBackoff(attempt);
            }
        }
    }

    private void sleepWithBackoff(int attempt) {
        try {
            // 지수 백오프: 1차 재시도 50ms, 2차 재시도 100ms — MAX_RETRY=3이라 200ms 구간은 발생하지 않는다.
            Thread.sleep(BASE_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockFailedException("좌석 낙관적 락 재시도 중 스레드가 중단되었습니다.");
        }
    }
}
