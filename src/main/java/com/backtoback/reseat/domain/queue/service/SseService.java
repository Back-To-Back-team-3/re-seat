package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class SseService {

    // 연결 유지 시간은 60초
    private static final long SSE_TIMEOUT_MILLIS = 60L * 1000L;
    private static final int SSE_SCHEDULER_POOL_SIZE = 8;

    // 여러 SSE 연결의 순번 전송 작업을 처리하는 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(SSE_SCHEDULER_POOL_SIZE);

    private final QueueService queueService;

    public SseEmitter streamMyQueue(Long gameId, Long userId) {

        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Runnable cancelTask = () -> {
            ScheduledFuture<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);
            }
        };

        sseEmitter.onCompletion(cancelTask);
        sseEmitter.onTimeout(cancelTask);
        sseEmitter.onError(t -> cancelTask.run());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                // 연결된 동안 현재 대기 상태를 rank 이벤트로 주기적으로 전송
                QueueStatusResponse response =
                        queueService.getMyQueueStatus(gameId, userId);

                sseEmitter.send(SseEmitter.event()
                        .name("rank")
                        .data(response)
                );

                if (response.isAdmitted()) {
                    // 입장이 허용되었다면 admit 이벤트로 토큰 정보 전달 후 연결 종료
                    sseEmitter.send(SseEmitter.event()
                            .name("admit")
                            .data(queueService.getAdmitEvent(gameId, userId))
                    );

                    sseEmitter.complete();
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        }, 0L, 3L, TimeUnit.SECONDS);

        futureRef.set(future);

        return sseEmitter;
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
    }
}
