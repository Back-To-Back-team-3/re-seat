package com.backtoback.reseat.domain.reservation.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * reservation.lock-strategy 값을 기동 시점에 LockStrategyType으로 즉시 검증한다.
 * <p>
 * {@code @ConditionalOnProperty}만으로는 값이 하나도 매치하지 않을 때 조용히 SeatLockStrategy 빈이 등록되지 않을 뿐 예외가 발생하지 않는다.
 * 이후 SeatHoldFacade 주입 실패로 기동은 실패하지만 원인이 불명확한 메시지로 나타난다.
 * 이 클래스는 같은 값을 미리 한 번 더 읽어 즉시 IllegalStateException을 던져 reservation.lock-strategy 값이 잘못됐다는 원인을 명확히 드러낸다.
 */
@Slf4j
@Configuration
public class LockStrategyValidationConfig {

    @Value("${reservation.lock-strategy:distributed}")
    private String lockStrategy;

    @PostConstruct
    void validate() {
        try {
            LockStrategyType.valueOf(lockStrategy.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "reservation.lock-strategy 설정값이 올바르지 않습니다: '" + lockStrategy
                    + "'. 허용값: distributed, pessimistic, optimistic",
                e
            );
        }
    }
}
