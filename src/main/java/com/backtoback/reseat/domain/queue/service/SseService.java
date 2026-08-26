package com.backtoback.reseat.domain.queue.service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.exception.QueueEntryCancellationNotAllowedException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryCancellationTokenRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryNotFoundException;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대기열 상태와 입장 허용 이벤트를 SSE로 주기적으로 전송하고 연결을 관리하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

    // 클라이언트의 SSE 연결을 유지하는 최대 시간
    private static final long SSE_TIMEOUT_MILLIS = 60L * 1000L;

    // 여러 SSE 연결의 순번 조회 작업을 동시에 실행할 스케줄러 스레드 수
    private static final int SSE_SCHEDULER_POOL_SIZE = 8;

    // 대기열 상태를 처음 조회하기 전의 지연 시간이며 이후 상태 전송 주기
    private static final long SSE_SEND_INTERVAL_SECONDS = 3L;

    // 마지막 SSE 연결 종료 후 대기열 이탈 처리까지 기다리는 재연결 유예시간
    private static final long SSE_RECONNECT_GRACE_MILLIS = 60L * 1000L;

    // SSE 스케줄러 종료 후 실행 중인 작업을 기다리는 최대 시간
    private static final long SSE_SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 5L;

    // SSE 연결별 대기 순번 조회 작업을 공동으로 실행하는 스케줄러다.
    // 연결마다 별도 스레드를 생성하지 않고 정해진 스레드 풀에서 주기 작업을 처리한다.
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(SSE_SCHEDULER_POOL_SIZE);

    // 동일 사용자와 경기에서 현재 유지 중인 SSE 연결 수를 관리한다.
    private final ConcurrentMap<QueueConnectionKey, Integer> activeConnectionCounts = new ConcurrentHashMap<>();

    // 마지막 연결 종료 후 재연결 유예시간 동안 실행을 보류한 대기열 이탈 작업을 관리한다.
    private final ConcurrentMap<QueueConnectionKey, ScheduledFuture<?>> pendingQueueExitTasks
        = new ConcurrentHashMap<>();

    private final QueueService queueService;

    /**
     * 사용자의 현재 대기 상태를 주기적으로 전송하고 입장 허용 시 토큰 정보를 전송한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 클라이언트와의 SSE 연결을 관리하는 emitter
     */
    public SseEmitter streamMyQueue(Long gameId, Long userId) {

        QueueConnectionKey connectionKey = new QueueConnectionKey(gameId, userId);
        registerConnection(connectionKey);

        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        // 연결 종료를 놓치지 않도록 종료 콜백을 주기 작업보다 먼저 등록한다.
        // 아직 생성되지 않은 주기 작업은 나중에 저장하여 종료 콜백에서 조회한다.
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        // 입장 허용으로 종료된 연결과 재연결 대상 연결을 구분한다.
        AtomicBoolean admitted = new AtomicBoolean(false);

        // SSE 연결 종료 시 주기 작업과 현재 연결 수를 함께 정리할 작업을 생성한다.
        Runnable cleanupTask = createConnectionCleanupTask(futureRef, connectionKey, admitted);

        // 정상 완료, 시간 만료, 전송 오류 중 어떤 방식으로 연결이 종료되더라도 동일한 정리 작업을 실행한다.
        sseEmitter.onCompletion(cleanupTask);
        sseEmitter.onTimeout(cleanupTask);
        sseEmitter.onError(t -> cleanupTask.run());

        // Kafka Consumer가 DB와 Redis 등록을 완료할 시간을 고려하여 첫 상태 조회를 즉시 실행하지 않고 지연한다.
        // 이후 연결이 유지되는 동안 지정된 주기에 맞춰 대기 상태를 반복해서 조회한다.
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                // 현재 순번 또는 입장 허용 여부가 담긴 대기 상태를 rank 이벤트로 전송한다.
                QueueStatusResponse response = queueService.getMyQueueStatus(gameId, userId);

                sseEmitter.send(SseEmitter.event().name("rank").data(response));

                if (response.isAdmitted()) {
                    // ADMITTED 상태가 확인되면 활성 Queue-Token 정보를 admit 이벤트로 추가 전송한다.
                    // 입장 정보 전송까지 끝나면 더 이상 순번을 조회할 필요가 없으므로 SSE 연결을 정상 종료한다.
                    sseEmitter.send(SseEmitter.event().name("admit").data(queueService.getAdmitEvent(gameId, userId)));

                    admitted.set(true);
                    sseEmitter.complete();
                }
            } catch (QueueEntryNotFoundException exception) {
                // HTTP 요청은 Kafka 발행 직후 반환되므로 SSE 연결 시점에는 Consumer의 대기열 등록이 끝나지 않았을 수도 있다.
                // 해당 상태를 일시적인 등록 지연으로 처리하여 연결을 종료하지 않고 다음 주기에 다시 조회한다.
            } catch (Exception e) {
                // 상태 조회 또는 SSE 전송 중 복구할 수 없는 예외가 발생하면 오류와 함께 연결을 종료한다.
                sseEmitter.completeWithError(e);
            }
        }, SSE_SEND_INTERVAL_SECONDS, SSE_SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 연결 종료 콜백에서 방금 생성한 주기 작업을 취소할 수 있도록 참조를 저장한다.
        futureRef.set(future);

        return sseEmitter;
    }

    /**
     * 앱 종료 시 SSE 순번 전송과 예약된 대기열 이탈 작업을 모두 종료한다.
     */
    @PreDestroy
    public void shutdownScheduler() {

        // 앱 종료 중 대기열 상태를 변경하지 않도록 실행 중인 작업과 예약된 작업에 즉시 종료를 요청한다.
        scheduler.shutdownNow();

        try {
            // 실행 중인 작업이 정리될 시간을 짧게 기다린다.
            if (!scheduler.awaitTermination(SSE_SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[SseService] 스케줄러 종료 대기 시간 초과 (timeoutSeconds={})", SSE_SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            log.warn("[SseService] 스케줄러 종료 대기 중 스레드가 중단되었습니다.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 동일 사용자와 경기의 SSE 연결 수를 증가시키고 예약된 대기열 이탈 작업을 취소한다.
     *
     * @param connectionKey SSE 연결 식별값
     */
    private void registerConnection(QueueConnectionKey connectionKey) {

        // 첫 연결이면 1로 등록하고 이미 연결이 있으면 기존 연결 수에 1을 더한다.
        activeConnectionCounts.merge(connectionKey, 1, Integer::sum);

        // 재연결된 사용자는 기존 순번을 유지하도록 예약된 대기열 이탈 작업을 취소한다.
        cancelPendingQueueExit(connectionKey);
    }

    /**
     * 동일 사용자와 경기에서 예약된 대기열 이탈 작업이 있으면 취소한다.
     *
     * @param connectionKey SSE 연결 식별값
     */
    private void cancelPendingQueueExit(QueueConnectionKey connectionKey) {

        ScheduledFuture<?> pendingQueueExitTask = pendingQueueExitTasks.remove(connectionKey);
        if (Objects.nonNull(pendingQueueExitTask)) {
            pendingQueueExitTask.cancel(false);
        }
    }

    /**
     * 동일 사용자와 경기의 SSE 연결 수를 감소시키고 마지막 연결이 종료됐는지 반환한다.
     *
     * @param connectionKey SSE 연결 식별값
     * @return 마지막 SSE 연결이 종료됐으면 true
     */
    private boolean removeConnection(QueueConnectionKey connectionKey) {

        AtomicBoolean lastConnectionClosed = new AtomicBoolean(false);

        // 여러 연결이 동시에 종료되어도 연결 수가 잚못 계산되지 않도록 한 번의 연산으로 처리한다.
        activeConnectionCounts.computeIfPresent(connectionKey, (key, count) -> {
            if (count <= 1) {
                // 마지막 연결이면 null을 반환해 Map에서 식별값을 삭제한다.
                lastConnectionClosed.set(true);
                return null;
            } else {
                // 다른 연결이 남아 있을경우 종료된 연결 하나만 감소시킨다.
                return count - 1;
            }
        });

        return lastConnectionClosed.get();
    }

    /**
     * SSE 연결 종료 시 주기 작업과 연결 수를 정리하고 입장 완료 여부에 따라 대기열 이탈을 예약하는 작업을 생성한다.
     *
     * @param futureRef 취소할 주기 작업 참조
     * @param connectionKey SSE 연결 식별값
     * @param admitted 입장 허용 이벤트 전송 완료 여부
     * @return SSE 연결 종료 시 실행할 정리 작업
     */
    private Runnable createConnectionCleanupTask(
        AtomicReference<ScheduledFuture<?>> futureRef,
        QueueConnectionKey connectionKey,
        AtomicBoolean admitted
    ) {

        // 하나의 연결에서 종료 콜백이 여러 번 호출될 수 있으므로 정리가 시작됐는지 저장한다.
        AtomicBoolean cleanupStarted = new AtomicBoolean(false);

        // SSE 연결이 끝나면 주기 작업 취소와 연결 수 감소를 함께 수행할 정리 작업을 정의한다.
        return () -> {
            // false에서 true로 처음 변경한 콜백만 정리 작업을 실행한다.
            if (cleanupStarted.compareAndSet(false, true)) {
                ScheduledFuture<?> future = futureRef.get();
                if (future != null) {
                    // 콜백이 주기 작업과 같은 스레드에서 실행될 수 있으므로 인터럽트 없이 반복 실행만 막는다.
                    future.cancel(false);
                }

                // 동일 사용자와 경기의 다른 연결은 유지하고 종료된 연결 하나만 현재 연결 수에서 제외한다.
                // 입장 허용으로 종료된 연결은 재연결 대상이 아니므로 이탈을 예약하지 않는다.
                if (removeConnection(connectionKey) && !admitted.get()) {
                    // 마지막 연결이 종료되면 재연결 유예시간 후 대기열 이탈 처리를 예약한다.
                    scheduleQueueExit(connectionKey);
                }
            }
        };
    }

    /**
     * 마지막 SSE 연결 종료 후 재연결 유예시간이 지나면 대기열 이탈 처리를 실행하도록 예약한다.
     *
     * @param connectionKey SSE 연결 식별값
     */
    private void scheduleQueueExit(QueueConnectionKey connectionKey) {

        AtomicReference<ScheduledFuture<?>> queueExitTaskRef = new AtomicReference<>();

        ScheduledFuture<?> queueExitTask = scheduler.schedule(() -> {
            try {
                // 유예시간 만료와 재연결이 겹칠 수 있으므로 실행 직전에 활성 연결을 다시 확인한다.
                if (Objects.nonNull(activeConnectionCounts.get(connectionKey))) {
                    return;
                }
                queueService.cancelMyQueue(connectionKey.gameId(), connectionKey.userId());
            } catch (QueueEntryCancellationNotAllowedException | QueueEntryCancellationTokenRequiredException e) {

                // 이미 취소됐거나 함께 회수할 활성 Queue-Token이 없어 추가 이탈 처리를 할 수 없는 경우 생략한다.
                log
                    .info(
                        "SSE 연결 종료 후 대기열 이탈 처리 대상이 아닙니다. gameId={}, userId={}",
                        connectionKey.gameId(),
                        connectionKey.userId()
                    );
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                log
                    .error(
                        "SSE 연결 종료 후 대기열 이탈 처리 실패. gameId={}, userId={}",
                        connectionKey.gameId(),
                        connectionKey.userId(),
                        e
                    );
            } finally {
                // 이후 예약된 작업을 제거하지 않도록 현재 실행한 작업과 일치할 때만 Map에서 삭제한다.
                pendingQueueExitTasks.remove(connectionKey, queueExitTaskRef.get());
            }

        }, SSE_RECONNECT_GRACE_MILLIS, TimeUnit.MILLISECONDS);

        queueExitTaskRef.set(queueExitTask);
        pendingQueueExitTasks.put(connectionKey, queueExitTask);

        // 예약 작업을 저장하기 전에 재연결된 경우에도 이탈되지 않도록 현재 연결을 다시 확인한다.
        if (Objects.nonNull(activeConnectionCounts.get(connectionKey))) {
            cancelPendingQueueExit(connectionKey);
        }
    }

    /**
     * 동일 사용자와 경기의 SSE 연결 수를 관리하기 위한 식별값
     *
     * @param gameId SSE 연결 대상 경기 ID
     * @param userId SSE 연결 사용자 ID
     */
    private record QueueConnectionKey(Long gameId, Long userId) {
    }
}
