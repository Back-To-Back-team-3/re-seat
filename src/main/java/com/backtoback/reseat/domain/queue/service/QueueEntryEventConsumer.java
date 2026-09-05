package com.backtoback.reseat.domain.queue.service;

import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventGameIdInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventIdRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventRequestedAtRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventUserIdInvalidException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.global.config.KafkaConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka 대기열 진입 요청 이벤트를 소비하여 실제 대기열 등록 또는 사용자 정책상의 거절 결과 저장을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueEntryEventConsumer {

    private final QueueService queueService;
    private final QueueEntryRejectionService queueEntryRejectionService;

    /**
     * 대기열 진입 이벤트의 등록 또는 거절 결과 저장을 처리하고 성공한 경우에만 Offset을 커밋한다.
     * <p>일시적인 처리 실패는 설정된 간격에 따라 재시도하고, 최종 실패한 이벤트는 DLT로 전달한다.
     * 이벤트 값이 잘못됐거나 경기 · 사용자가 존재하지 않는 경우에는 재시도해도 성공할 수 없으므로 즉시 DLT로 전달한다.</p>
     * <p>정책상 거절된 최신 요청은 거절 사유를 저장하고,
     * 정상 처리된 최신 요청은 요청 식별자를 정리한다.</p>
     *
     * @param record Kafka Consumer Record
     * @param acknowledgment 수동 Offset 커밋 객체
     */
    @RetryableTopic(
        attempts = "4",
        // 최초 실행을 포함한 총 시도 횟수
        backoff = @Backoff(
            // 다음 재시도 까지 기다릴 시간
            delay = 2000,
            multiplier = 2.0
        ),
        numPartitions = "3",
        // 자동 생성되는 재시도 토픽과 DLT의 파티션 수
        replicationFactor = "1",
        // 단일 Kafka 브로커 환경에 맞춘 재시도 토픽과 DLT 복제본 수
        exclude = {
            QueueEntryEventRequiredException.class,
            QueueEntryEventIdRequiredException.class,
            QueueEntryEventGameIdInvalidException.class,
            QueueEntryEventUserIdInvalidException.class,
            QueueEntryEventRequestedAtRequiredException.class,
            GameNotFoundException.class,
            UserNotFoundException.class
        },
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
        topics = KafkaConfig.QUEUE_ENTRY_REQUESTED_TOPIC,
        groupId = KafkaConfig.QUEUE_ENTRY_CONSUMER_GROUP,
        concurrency = "3"
    )
    public void consume(ConsumerRecord<String, QueueEntryRequestedEvent> record, Acknowledgment acknowledgment) {

        QueueEntryRequestedEvent event = record.value();

        String eventId = event == null ? "null" : String.valueOf(event.eventId());

        try {
            // 신규 등록, 기존 대기열 복구 · 무시 또는 정책상 거절을 처리한다.
            // 정상적으로 처리된 경우만 offset을 커밋하고 예외가 발생하면 커밋하지 않아 재시도 또한 DLT 처리가 이어지게 한다.
            Optional<QueueEntryRejectionReason> rejectionReason = queueService.registerQueueEntry(event);

            // 최신 요청과 일치하는 이벤트만 거절 결과를 저장하거나 요청 식별자를 정리한 뒤 Offset을 커밋한다.
            rejectionReason.ifPresentOrElse(
                reason -> queueEntryRejectionService.saveRejectionIfLatest(event.gameId(), event.userId(), event.eventId(), reason),
                () -> queueEntryRejectionService.completeRequestIfLatest(event.gameId(), event.userId(), event.eventId())
            );

            acknowledgment.acknowledge();

            log
                .info(
                    "대기열 진입 이벤트 처리 완료: eventId={}, partition={}, offset={}",
                    eventId,
                    record.partition(),
                    record.offset()
                );
        } catch (RuntimeException exception) {
            log
                .error(
                    "대기열 진입 이벤트 처리 실패: eventId={}, partition={}, offset={}",
                    eventId,
                    record.partition(),
                    record.offset(),
                    exception
                );

            // 예외를 다시 전달해야 Spring Kafka의 재시도 및 DLT 처리가 동작한다.
            throw exception;
        }
    }

    /**
     * 재시도가 모두 끝난 대기열 진입 이벤트를 기록한다.
     *
     * @param event 최종 처리에 실패한 이벤트
     */
    @DltHandler
    public void handleDlt(QueueEntryRequestedEvent event) {

        // 현재 범위에서는 DLT 이벤트 로그만 기록한다.
        // TODO: 자동 재처리와 별도 저장은 정책 확정 후 후속작업에서 진행한다.
        if (event == null) {
            log.error("비어 있는 대기열 진입 이벤트가 DLT에 도착했습니다.");
            return;
        }

        log
            .error(
                "대기열 진입 이벤트 DLT 도착: eventId={}, gameId={}, userId={}",
                event.eventId(),
                event.gameId(),
                event.userId()
            );
    }
}
