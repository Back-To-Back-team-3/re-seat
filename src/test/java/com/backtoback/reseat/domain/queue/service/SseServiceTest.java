package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SseService의 재연결 유예시간과 만료 후 대기열 이탈 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SseService")
public class SseServiceTest {

    private static final Long GAME_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Duration RECONNECT_GRACE = Duration.ofSeconds(150L);

    @Mock
    private QueueService queueService;
    @Mock
    private QueueEntryRejectionService queueEntryRejectionService;
    @Mock
    private TaskScheduler scheduler;
    @InjectMocks
    private SseService sseService;

    /**
     * 상태 전송 반복 작업과 대기열 이탈 작업을 반환하도록 스케줄러 Mock 동작을 설정한다.
     *
     * @param statusSendFuture 상태 전송 반복 작업 예약 결과
     * @param queueExitFuture 대기열 이탈 작업 예약 결과
     */
    private void givenScheduledFutures(ScheduledFuture<?> statusSendFuture, ScheduledFuture<?> queueExitFuture) {

        doReturn(statusSendFuture)
            .when(scheduler)
            .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        doReturn(queueExitFuture).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    /**
     * 첫 연결과 재연결의 상태 전송 작업 및 대기열 이탈 작업을 반환하도록 스케줄러 Mock 동작을 설정한다.
     *
     * @param firstStatusSendFuture 첫 연결의 상태 전송 반복 작업 예약 결과
     * @param reconnectedStatusSendFuture 재연결의 상태 전송 반복 작업 예약 결과
     * @param queueExitFuture 대기열 이탈 작업 예약 결과
     */
    private void givenScheduledFutures(
        ScheduledFuture<?> firstStatusSendFuture,
        ScheduledFuture<?> reconnectedStatusSendFuture,
        ScheduledFuture<?> queueExitFuture
    ) {

        doReturn(firstStatusSendFuture, reconnectedStatusSendFuture)
            .when(scheduler)
            .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        doReturn(queueExitFuture).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    /**
     * Spring MVC 컨테이너가 호출하는 SSE 연결 종료 콜백을 실행한다.
     *
     * @param sseEmitter 종료할 SSE 연결
     */
    private void completeSseConnection(SseEmitter sseEmitter) {

        Runnable completionCallback = (Runnable)ReflectionTestUtils.getField(sseEmitter, "completionCallback");

        assertThat(completionCallback).isNotNull();
        completionCallback.run();
    }

    // ---------- 재연결 유예시간 및 만료 처리 ----------

    @Test
    @DisplayName("마지막 SSE 연결이 종료되면 대기열 이탈 작업을 150초 뒤로 예약한다.")
    void streamMyQueue_onDisconnect_schedulesExit() {

        // given
        ScheduledFuture<?> statusSendFuture = Mockito.mock(ScheduledFuture.class);
        ScheduledFuture<?> queueExitFuture = Mockito.mock(ScheduledFuture.class);

        givenScheduledFutures(statusSendFuture, queueExitFuture);

        // when
        Instant verificationStartedAt = Instant.now();

        SseEmitter sseEmitter = sseService.streamMyQueue(GAME_ID, USER_ID);
        completeSseConnection(sseEmitter);

        Instant verificationFinishedAt = Instant.now();

        // then
        ArgumentCaptor<Instant> queueExitAtCaptor = ArgumentCaptor.forClass(Instant.class);

        then(scheduler).should().schedule(any(Runnable.class), queueExitAtCaptor.capture());
        Instant queueExitAt = queueExitAtCaptor.getValue();

        // 150초는 마지막 연결 종료 후 기존 대기 상태를 보존하는 재연결 허용시간이다.
        // Instant.now()의 실행 시간 차이를 고려해 하나의 고정 시각이 아닌 범위로 검증한다.
        assertThat(queueExitAt)
            .isBetween(verificationStartedAt.plus(RECONNECT_GRACE), verificationFinishedAt.plus(RECONNECT_GRACE));

        then(queueService).should(never()).cancelMyQueue(GAME_ID, USER_ID);
    }

    @Test
    @DisplayName("재연결 유예시간 안에 같은 사용자가 재연결하면 예약된 대기열 이탈 작업을 취소한다.")
    void streamMyQueue_onReconnect_cancelsExit() {

        // given
        ScheduledFuture<?> firstStatusSendFuture = Mockito.mock(ScheduledFuture.class);
        ScheduledFuture<?> pendingQueueExitFuture = Mockito.mock(ScheduledFuture.class);
        ScheduledFuture<?> reconnectedStatusSendFuture = Mockito.mock(ScheduledFuture.class);

        givenScheduledFutures(firstStatusSendFuture, reconnectedStatusSendFuture, pendingQueueExitFuture);

        SseEmitter firstEmitter = sseService.streamMyQueue(GAME_ID, USER_ID);
        completeSseConnection(firstEmitter);

        // when
        SseEmitter reconnectedEmitter = sseService.streamMyQueue(GAME_ID, USER_ID);

        // then
        // 이전 이탈 예약이 남으면 재연결한 사용자의 대기 상태가 잘못 취소될 수 있다.
        then(pendingQueueExitFuture).should().cancel(false);
        then(queueService).should(never()).cancelMyQueue(GAME_ID, USER_ID);

        // 재연결은 새 SSE 연결을 만들지만 기존 사용자의 대기 상태는 유지한다.
        assertThat(reconnectedEmitter).isNotSameAs(firstEmitter);
    }

    @Test
    @DisplayName("재연결 유예시간이 만료되면 기존 대기열 취소 흐름을 실행한다.")
    void streamMyQueue_onGraceExpiry_cancelsQueue() {

        // given
        ScheduledFuture<?> statusSendFuture = Mockito.mock(ScheduledFuture.class);
        ScheduledFuture<?> queueExitFuture = Mockito.mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> queueExitTaskCaptor = ArgumentCaptor.forClass(Runnable.class);

        givenScheduledFutures(statusSendFuture, queueExitFuture);

        SseEmitter sseEmitter = sseService.streamMyQueue(GAME_ID, USER_ID);
        completeSseConnection(sseEmitter);

        then(scheduler).should().schedule(queueExitTaskCaptor.capture(), any(Instant.class));
        Runnable queueExitTask = queueExitTaskCaptor.getValue();

        // when
        // 시간 경과 자체가 아니라 만료 시 실행되는 정책을 검증하기 위해 예약 작업을 직접 실행한다.
        queueExitTask.run();

        // then
        // 만료 처리는 취소 로직을 복제하지 않고 기존 QueueService 정책에 위임한다.
        then(queueService).should().cancelMyQueue(GAME_ID, USER_ID);
    }
}
