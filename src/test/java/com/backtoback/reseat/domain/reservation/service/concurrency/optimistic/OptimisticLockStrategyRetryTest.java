package com.backtoback.reseat.domain.reservation.service.concurrency.optimistic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.backtoback.reseat.domain.reservation.exception.LockFailedException;
import com.backtoback.reseat.domain.reservation.service.lock.OptimisticLockStrategy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * {@link OptimisticLockStrategy}의 재시도 로직 단위 테스트.
 * <p>
 * 락 보호 대상인 {@code ReservationService.holdSeats()}를 직접 호출하지 않고 {@code Supplier<T> action}을
 * Mockito로 모킹해, 버전 충돌·재시도 상한 초과 상황을 결정론적으로 재현한다.
 * DB·Redis 등 인프라 없이 순수 JVM에서 실행되므로 {@code OptimisticLockStrategyConcurrencyTest}(통합 테스트)보다
 * 훨씬 빠르고, 재시도 횟수(3회)라는 경계값 자체를 안정적으로 검증할 수 있다.
 */
class OptimisticLockStrategyRetryTest {

    // MeterRegistry는 재시도 카운터 증가만 담당하고 검증 대상은 아니므로,
    // Mock이 아닌 실제 SimpleMeterRegistry(인메모리 구현체)를 사용해 불필요한 스텁 설정을 없앤다.
    private final OptimisticLockStrategy strategy = new OptimisticLockStrategy(new SimpleMeterRegistry());

    @Test
    @DisplayName("충돌이 2회 발생하고 3번째 시도에서 성공하면 정상 반환한다")
    @SuppressWarnings("unchecked")
    // Mockito가 제네릭 Supplier<T>를 raw type으로만 모킹할 수 있어 발생하는 경고 — 안전하게 무시 가능
    void should_returnResult_when_succeedsWithinMaxRetry() {
        // given
        // action.get() 호출을 1·2번째는 실패, 3번째는 성공으로 스텁
        // 실제로는 ReservationService.holdSeats()가 커밋 시점에 버전 충돌을 겪는 상황을 흉내낸다.
        // 생성자 인자(Object.class, 1L)는 예외 메시지에 쓰일 "충돌한 엔티티 타입·식별자" 자리이며,
        // 이 테스트에서는 값 자체가 아니라 예외 타입만 검증 대상이다.
        Supplier<String> action = mock(Supplier.class);
        when(action.get())
            .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L))
            .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L))
            .thenReturn("HELD");

        // when
        // gameSeatIds는 낙관적 락 전략에서 실제로 락 획득에 쓰이지 않으므로(사전 락 없음), 임의의 값(List.of(1L))을 넣어도 로직에 영향이 없다.
        String result = strategy.executeWithLocks(List.of(1L), action);

        // then
        // 3번째 시도에서 반환한 "HELD" 값이 그대로 올라와야 하고, action.get()이 정확히 3번(실패 2번 + 성공 1번) 호출됐어야
        // 재시도 횟수가 설계대로 소진되지 않고 성공 시점에 즉시 멈췄다는 것을 증명한다.
        assertThat(result).isEqualTo("HELD");
        verify(action, times(3)).get();
    }

    @Test
    @DisplayName("충돌이 MAX_RETRY(3)회 연속 발생하면 LockFailedException으로 수렴한다")
    @SuppressWarnings("unchecked")
    void should_throwLockFailedException_when_conflictExceedsMaxRetry() {
        // given
        // 매번 호출마다 동일한 충돌 예외를 반환하도록 스텁 경합이 재시도 상한(3회) 동안 해소되지 않는 "혼잡이 극심한 상황"을 재현한다.
        Supplier<String> action = mock(Supplier.class);
        when(action.get()).thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        // when & then
        // 재시도 상한을 넘기면 분산락·비관적 락과 동일하게 LockFailedException으로 수렴해야 SeatHoldFacade의 예외 처리 분기가 전략에 무관하게 하나로 유지된다.
        // action.get()이 정확히 3번 호출됐는지도 함께 확인해, 재시도 루프가 4번째 호출 없이 딱 상한에서 멈췄는지(off-by-one 오류 없는지)를 검증한다.
        assertThatThrownBy(() -> strategy.executeWithLocks(List.of(1L), action))
            .isInstanceOf(LockFailedException.class);
        verify(action, times(3)).get();
    }

    @Test
    @DisplayName("충돌이 MAX_RETRY(3)회 연속 발생하면 metric은 실제 재시도 횟수(2회)만큼만 증가한다")
    @SuppressWarnings("unchecked")
    void should_incrementMetricOnlyOnActualRetries_when_conflictExceedsMaxRetry() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OptimisticLockStrategy strategyWithRealMetric = new OptimisticLockStrategy(meterRegistry);
        Supplier<String> action = mock(Supplier.class);
        when(action.get()).thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        // when
        assertThatThrownBy(() -> strategyWithRealMetric.executeWithLocks(List.of(1L), action))
            .isInstanceOf(LockFailedException.class);

        // then
        // 총 시도 3회 중 실제 재시도(sleep 발생)는 2회뿐이므로, metric도 2회만 증가해야 한다.
        assertThat(meterRegistry.counter("optimistic_lock_retry_total").count()).isEqualTo(2.0);
    }
}
