package com.backtoback.reseat.domain.reservation.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.backtoback.reseat.domain.reservation.service.lock.OptimisticLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.PessimisticLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.RedissonSeatLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * reservation.lock-strategy 설정값별로 SeatLockStrategy 빈 등록 여부를 검증하는 단위 테스트.
 */
@DisplayName("락 전략 스위칭 설정")
class LockStrategySwitchConfigTest {

    // LockStrategyValidationConfig도 함께 등록해야, 잘못된 값을 검증하는 테스트가 실제로 이 클래스의 동작을 검증하게 된다.
    // 없으면 SeatLockStrategy 빈이 없다는 사실만으로 hasFailed()가 통과해버려, 명확한 에러 메시지 기능을 전혀 검증하지 못하는 테스트가 된다.
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

    @ParameterizedTest(name = "lock-strategy={0}일 때 해당 전략 빈만 등록된다")
    @EnumSource(LockStrategyType.class)
    @DisplayName("설정값과 일치하는 전략 빈만 등록되고, 나머지 두 전략은 등록되지 않는다.")
    void registersOnlyMatchingStrategy(LockStrategyType type) {
        // given
        // 파라미터로 들어온 enum 값을 실제 프로퍼티 문자열(소문자)로 변환한다.
        String propertyValue = type.name().toLowerCase();

        // when
        contextRunner.withPropertyValues("reservation.lock-strategy=" + propertyValue).run(context -> {
            // then
            // 세 전략이 모두 컨텍스트에 후보로 존재하지만, 조건에 맞는 단 하나만 실제로 등록됐는지 확인한다.
            assertThat(context).hasSingleBean(SeatLockStrategy.class);
            assertThat(context.getBean(SeatLockStrategy.class).getClass().getSimpleName())
                .isEqualTo(expectedBeanName(type));
        });
    }

    @Test
    @DisplayName("잘못된 값이 설정되면 LockStrategyValidationConfig가 명확한 원인 메시지로 기동을 실패시킨다.")
    void failsToStartWithClearMessageOnInvalidValue() {
        // given & when
        // 오타 값을 줘서, 세 전략 모두 조건 불일치로 등록되지 않는 상황을 재현한다.

        // then
        contextRunner.withPropertyValues("reservation.lock-strategy=invalid-value").run(context -> {
            assertThat(context).hasFailed();
            // BeanCreationException의 바로 한 단계 아래(cause)가 LockStrategyValidationConfig가 던진 IllegalStateException인지 확인한다.
            assertThat(context.getStartupFailure())
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reservation.lock-strategy 설정값이 올바르지 않습니다");
        });
    }

    private String expectedBeanName(LockStrategyType type) {
        return switch (type) {
            case DISTRIBUTED -> "RedissonSeatLockStrategy";
            case PESSIMISTIC -> "PessimisticLockStrategy";
            case OPTIMISTIC -> "OptimisticLockStrategy";
        };
    }
}
