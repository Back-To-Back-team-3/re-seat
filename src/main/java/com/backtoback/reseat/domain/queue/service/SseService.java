package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.exception.QueueEntryNotFoundException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 대기열 순번과 입장 허용 이벤트를 SSE로 전송하는 서비스
 */
@Service
@RequiredArgsConstructor
public class SseService {

    // 연결 유지 시간은 60초
    private static final long SSE_TIMEOUT_MILLIS = 60L * 1000L;
    private static final int SSE_SCHEDULER_POOL_SIZE = 8;
    private static final long SSE_SEND_INTERVAL_SECONDS = 3L;

    // 여러 SSE 연결의 순번 전송 작업을 처리하는 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(SSE_SCHEDULER_POOL_SIZE);

    private final QueueService queueService;

    /**
     * 사용자의 현재 대기 순번을 주기적으로 전송하고, 입장 허용 시 admit 이벤트를 전송한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return SSE 연결 emitter
     */
    public SseEmitter streamMyQueue(Long gameId, Long userId) {

        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        // ScheduledFuture 생성 전 콜백을 등록해야 하므로 AtomicReference로 작업 참조를 나중에 채운다.
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
                // 연결된 동안 현재 대기 상태를 rank 이벤트로 주기적으로 전송한다.
                QueueStatusResponse response =
                        queueService.getMyQueueStatus(gameId, userId);

                sseEmitter.send(SseEmitter.event()
                        .name("rank")
                        .data(response)
                );

                if (response.isAdmitted()) {
                    // 입장이 허용 시 admit 이벤트로 토큰 정보를 전달하고 연결을 종료한다.
                    sseEmitter.send(SseEmitter.event()
                            .name("admit")
                            .data(queueService.getAdmitEvent(gameId, userId))
                    );

                    sseEmitter.complete();
                }
            } catch (QueueEntryNotFoundException exception) {
                // Kafka Consumer의 대기열 등록이 아직 끝나지 않은 경우
                // SSE 연결을 종료하지 않고 다음 주기에 다시 조회한다.
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
        }, SSE_SEND_INTERVAL_SECONDS,
                SSE_SEND_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        futureRef.set(future);

        return sseEmitter;
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
    }
}
