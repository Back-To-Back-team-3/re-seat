package com.backtoback.reseat.global.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("자동 스케줄링 활성화 설정")
public class SchedulingActivationConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("테스트 환경에서는 자동 스케줄링을 비활성화한다.")
    void disablesAutomaticSchedulingInTestProfile() {
        assertThat(applicationContext.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
            .isFalse();
        assertThat(applicationContext.containsBean("taskScheduler")).isTrue();
        assertThat(applicationContext.containsBean("sseTaskScheduler")).isTrue();
    }
}
