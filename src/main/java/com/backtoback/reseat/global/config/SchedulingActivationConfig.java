package com.backtoback.reseat.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션의 자동 스케줄링 활성화 설정.
 * <p>{@code scheduling.enabled=false}로 설정한 환경에서는 {@code @Scheduled} 메서드의 자동 실행을 비활성화한다.
 * 설정값이 없으면 운영 환경의 기존 동작을 유지하기 위해 자동 스케줄링을 활성화한다.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SchedulingActivationConfig {}
