package com.backtoback.reseat.domain.queue.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka로 전달하는 대기열 진입 요청 이벤트
 *
 * @param eventId 이벤트 로그 추적에 사용하는 고유 식별자
 * @param gameId 대기열에 진입할 경기 ID
 * @param userId 대기열 진입을 요청한 사용자 ID
 * @param requestedAt 최초 진입 요청 시간이며 Redis ZSet 대기 순서의 기준
 */
public record QueueEntryRequestedEvent(UUID eventId, Long gameId, Long userId, Instant requestedAt) {
}
