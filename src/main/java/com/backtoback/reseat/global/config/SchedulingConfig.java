package com.backtoback.reseat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Scheduling 설정.
 * <p>
 */
@Configuration
public class SchedulingConfig {

    private static final int SSE_SCHEDULER_POOL_SIZE = 8;
    private static final int SSE_SCHEDULER_SHUTDOWN_TIME_SECONDS = 5;

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

    /**
     * SSE 상태 전송과 재연결 유예 작업을 실행하는 전용 스레드 풀,
     * <p>연결별 반복 작업이 일반 스케줄러 작업을 지연시키지 않도록
     * 기존 taskScheduler와 분리하여 관리한다.</p>
     *
     * @return SSE 예약 작업용 ThreadPoolTaskScheduler Bean
     */
    @Bean
    public ThreadPoolTaskScheduler sseTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SSE_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("reseat-sse-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(SSE_SCHEDULER_SHUTDOWN_TIME_SECONDS);
        // 연결 종료로 취소된 작업이 실행 시각까지 스케줄러 큐를 점유하지 않도록 즉시 제거한다.
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
