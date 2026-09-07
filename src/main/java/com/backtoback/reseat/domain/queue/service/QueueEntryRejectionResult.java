package com.backtoback.reseat.domain.queue.service;

import java.util.UUID;

import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;

/**
 * Consumer가 저장한 대기열 진입 거절 결과.
 *
 * @param eventId 거절된 대기열 진입 요청 이벤트 ID
 * @param reason 대기열 진입 거절 사유
 */
public record QueueEntryRejectionResult(UUID eventId, QueueEntryRejectionReason reason) {
}
