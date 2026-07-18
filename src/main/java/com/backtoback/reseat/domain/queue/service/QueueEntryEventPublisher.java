package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.global.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 대기열 진입 요청 이벤트를 Kafka로 발행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueEntryEventPublisher {

    private final KafkaTemplate<String, QueueEntryRequestedEvent> kafkaTemplate;

    /**
     * 경기 ID를 메시지 Key로 사용하여 대기열 진입 요청 이벤트를 발행한다.
     *
     * @param event 대기열 진입 요청 이벤트
     * @return Kafka 발행 결과
     */
    public CompletableFuture<SendResult<String, QueueEntryRequestedEvent>> publish(
            QueueEntryRequestedEvent event
    ) {

        String messageKey = String.valueOf(event.gameId());

        return kafkaTemplate.send(
                        KafkaConfig.QUEUE_ENTRY_REQUESTED_TOPIC,
                        messageKey,
                        event
                )
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error(
                                "대기열에 진입 이벤트 발행 실패: eventId={}, gameId={}, userId={}",
                                event.eventId(), event.gameId(), event.userId(), exception
                        );
                        return;
                    }

                    log.info(
                            "대기열 진입 이벤트 발행 완료: eventId={}, partition={}, offset={}",
                            event.eventId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset()
                    );
                });
    }

}
