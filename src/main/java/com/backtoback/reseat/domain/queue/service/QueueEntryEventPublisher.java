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

        // 같은 경기의 진입 이벤트가 동일한 원본 토픽 파티션으로 전달되도록 gameId를 메시지 Key로 사용한다.
        // 재시도 토픽으로 이동한 실패 이벤트까지 전체 처리 순서가 보장되지 않는다.
        String messageKey = String.valueOf(event.gameId());

        // 반환되는 Future는 Kafka 브로커 전송 결과를 나타낸다.
        // Consumer의 DB 이력 및 Redis 대기열 등록 완료 까지는 보장하지 않는다.
        return kafkaTemplate.send(
                        KafkaConfig.QUEUE_ENTRY_REQUESTED_TOPIC,
                        messageKey,
                        event
                )
                // 발행 결과를 추적하기 위해 로그만 기록하고 성공 또는 실패 결과는 호출자에게 그대로 전파한다.
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
