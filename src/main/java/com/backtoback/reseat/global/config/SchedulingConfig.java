package com.backtoback.reseat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Scheduling 설정.
 * <p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 스케줄러 전용 스레드 풀.
     * <p>
     * poolSize = 3: 현재 확정된 스케줄러(만료 회수 + 예매 오픈/마감 2종)를 기준으로 설정한다.
     * 스케줄러가 추가되면 poolSize를 함께 올린다.
     * threadNamePrefix로 로그에서 스케줄러 스레드를 빠르게 식별한다.
     *
     * @return ThreadPoolTaskScheduler 빈
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("reseat-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
