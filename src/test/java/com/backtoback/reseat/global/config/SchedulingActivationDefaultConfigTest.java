package com.backtoback.reseat.global.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * scheduling.enabled 기본값에 따른 자동 스케줄링 활성화를 검증한다.
 */
@DisplayName("자동 스케줄링 기본 활성화 설정")
public class SchedulingActivationDefaultConfigTest {

    private final ApplicationContextRunner contextRunner
        = new ApplicationContextRunner().withUserConfiguration(SchedulingActivationConfig.class);

    @Test
    @DisplayName("스케줄링 설정이 없으면 자동 스케줄링을 활성화한다.")
    void enablesAutomaticSchedulingWhenPropertyIsMissing() {

        // when & then
        // 설정 미지정 시 matchIfMissing=true가 적용되는지를 확인하는 테스트다.
        contextRunner.run(context -> {
            assertThat(context).hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
        });
    }
}
