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

    // 클라이언트의 SSE 연결을 유지하는 최대 시간
    private static final long SSE_TIMEOUT_MILLIS = 60L * 1000L;

    // 여러 SSE 연결의 순번 조회 작업을 동시에 실행할 스케줄러 스레드 수
    private static final int SSE_SCHEDULER_POOL_SIZE = 8;

    // 대기열 상태를 처음 조회하기 전의 지연 시간이며 이후 상태 전송 주기
    private static final long SSE_SEND_INTERVAL_SECONDS = 3L;

    // SSE 연결별 대기 순번 조회 작업을 공동으로 실행하는 스케줄러다.
    // 연결마다 별도 스레드를 생성하지 않고 정해진 스레드 풀에서 주기 작업을 처리한다.
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(SSE_SCHEDULER_POOL_SIZE);

    private final QueueService queueService;

    /**
     * 사용자의 현재 대기 상태를 주기적으로 전송하고 입장 허용 시 토큰 정보를 전송한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 클라이언트와의 SSE 연결을 관리하는 emitter
     */
    public SseEmitter streamMyQueue(Long gameId, Long userId) {

        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        // ScheduledFuture 생성 전 콜백을 등록해야 하므로 AtomicReference로 작업 참조를 나중에 채운다.
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        // SSE 연결이 끝난 이후에도 순번 조회 작업이 계속 실행되지 않도록 등록된 주기 작업을 취소한다.
        Runnable cancelTask = () -> {
            ScheduledFuture<?> future = futureRef.get();
            if (future != null) {
                // 이미 작업이 실행 중인 경우네는 해당 스레드에 인터럽트를 요청하고 이후 반복 실행을 중단한다.
                future.cancel(true);
            }
        };

        // 정상 완료, 시간 만료, 전송 오류 중 어떤 방식으로 연결이 종료되더라도 주기 작업을 정리한다.
        sseEmitter.onCompletion(cancelTask);
        sseEmitter.onTimeout(cancelTask);
        sseEmitter.onError(t -> cancelTask.run());

        // Kafka Consumer가 DB와 Redis 등록을 완료할 시간을 고려하여 첫 상태 조회를 즉시 실행하지 않고 지연한다.
        // 이후 연결이 유지되는 동안 지정된 주기에 맞춰 대기 상태를 반복해서 조회한다.
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                // 현재 순번 또는 입장 허용 여부가 담긴 대기 상태를 rank 이벤트로 전송한다.
                QueueStatusResponse response =
                        queueService.getMyQueueStatus(gameId, userId);

                sseEmitter.send(SseEmitter.event()
                        .name("rank")
                        .data(response)
                );

                if (response.isAdmitted()) {
                    // ADMITTED 상태가 확인되면 활성 Queue-Token 정보를 admit 이벤트로 추가 전송한다.
                    // 입장 정보 전송까지 끝나면 더 이상 순번을 조회할 필요가 없으므로 SSE 연결을 정상 종료한다.
                    sseEmitter.send(SseEmitter.event()
                            .name("admit")
                            .data(queueService.getAdmitEvent(gameId, userId))
                    );

                    sseEmitter.complete();
                }
            } catch (QueueEntryNotFoundException exception) {
                // HTTP 요청은 Kafka 발행 직후 반환되므로 SSE 연결 시점에는 Consumer의 대기열 등록이 끝나지 않았을 수도 있다.
                // 해당 상태를 일시적인 등록 지연으로 처리하여 연결을 종료하지 않고 다음 주기에 다시 조회한다.
            } catch (Exception e) {
                // 상태 조회 또는 SSE 전송 중 복구할 수 없는 예외가 발생하면 오류와 함께 연결을 종료한다.
                sseEmitter.completeWithError(e);
            }
        }, SSE_SEND_INTERVAL_SECONDS,
                SSE_SEND_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        // 연결 종료 콜백에서 방금 생성한 주기 작업을 취소할 수 있도록 참조를 저장한다.
        futureRef.set(future);

        return sseEmitter;
    }

    /**
     * 애플리케이션 종료 시 SSE 순번 전송 스케줄러를 종료한다.
     */
    @PreDestroy
    public void shutdownScheduler() {
        // 새로운 SSE 주기 작업의 등록을 막고 현재 등록된 작업들을 종료 절차로 전환한다.
        scheduler.shutdown();
    }
}
