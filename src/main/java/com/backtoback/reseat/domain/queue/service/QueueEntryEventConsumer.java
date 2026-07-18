package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.domain.queue.exception.QueueInvalidEventException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.global.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

/**
 * Kafka 대기열 진입 요청 이벤트를 소비하여 실제 대기열 등록을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueEntryEventConsumer {

    private final QueueService queueService;

    /**
     * 대기열 진입 이벤트를 처리하고 성공한 경우에만 Offset을 커밋한다.
     *
     * @param record Kafka Consumer Record
     * @param acknowledgment 수동 Offset 커밋 객체
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(
                    delay = 2000,
                    multiplier = 2.0
            ),
            numPartitions = "3",
            replicationFactor = "1",
            exclude = {
                    QueueInvalidEventException.class,
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
    public void consume(
            ConsumerRecord<String, QueueEntryRequestedEvent> record,
            Acknowledgment acknowledgment
    ) {

        QueueEntryRequestedEvent event = record.value();

        String eventId = event == null ? "null" : String.valueOf(event.eventId());

        try {
            queueService.registerQueueEntry(event);
            acknowledgment.acknowledge();

            log.info(
                    "대기열 진입 이벤트 처리 완료: eventId={}, partition={}, offset={}",
                    eventId, record.partition(), record.offset()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "대기열 진입 이벤트 처리 실패: eventId={}, partition={}, offset={}",
                    eventId, record.partition(), record.offset(), exception
            );

            throw exception;
        }
    }

    /**
     * 재시도를 모두 싪패한 대기열 진입 이벤트를 기록한다.
     *
     * <p>TODO: 재처리 정책을 만들거나 후속작업으로 남긴다.</p>
     * @param event 최종 처리에 실패한 이벤트
     */
    @DltHandler
    public void handleDlt(QueueEntryRequestedEvent event) {

        if (event == null) {
            log.error("비어 있는 대기열 진입 이벤트가 DLT에 도착했습니다.");
            return;
        }

        log.error(
                "대기열 진입 이벤트 DLT 도착: eventId={}, gameId={}, userId={}",
                event.eventId(), event.gameId(), event.userId()
        );
    }
}
