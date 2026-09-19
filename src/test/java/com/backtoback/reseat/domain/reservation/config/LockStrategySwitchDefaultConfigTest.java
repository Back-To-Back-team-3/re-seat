package com.backtoback.reseat.domain.reservation.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.backtoback.reseat.domain.reservation.service.lock.OptimisticLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.PessimisticLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.RedissonSeatLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * reservation.lock-strategy 기본값(누락 시 distributed)을 검증하는 단위 테스트.
 */
@DisplayName("락 전략 기본 스위칭 설정")
class LockStrategySwitchDefaultConfigTest {

    private final ApplicationContextRunner contextRunner
        = new ApplicationContextRunner()
            .withUserConfiguration(
                RedissonSeatLockStrategy.class,
                PessimisticLockStrategy.class,
                OptimisticLockStrategy.class,
                LockStrategyValidationConfig.class
            )
            .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
            .withBean(GameSeatRepository.class, () -> mock(GameSeatRepository.class))
            .withBean(MeterRegistry.class, () -> mock(MeterRegistry.class));

    @Test
    @DisplayName("설정값이 없으면 RedissonSeatLockStrategy(distributed)로 기동하고, 검증도 통과한다.")
    void registersDistributedStrategyWhenPropertyIsMissing() {
        // when & then
        // 설정 미지정 시 RedissonSeatLockStrategy의 matchIfMissing=true가 적용되는지,
        // LockStrategyValidationConfig의 기본값(:distributed)과 어긋나지 않아 기동이 실패하지 않는지를 함께 확인한다.
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SeatLockStrategy.class);
            assertThat(context.getBean(SeatLockStrategy.class)).isInstanceOf(RedissonSeatLockStrategy.class);
        });
    }
}
