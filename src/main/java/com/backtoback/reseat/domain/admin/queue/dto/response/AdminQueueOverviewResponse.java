package com.backtoback.reseat.domain.admin.queue.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.BookingStatus;

/**
 * 관리자 경기별 대기열 현황 응답.
 *
 * @param gameId 경기 ID
 * @param bookingStatus 경기 예매 상태
 * @param waitingCount 현재 Redis 대기 인원
 * @param usableAdmissionCount 현재 사용할 수 있는 Queue-Token 수
 * @param admittedToday 오늘 발급된 Queue-Token 수
 * @param collectedAt 현황 조회 시간
 */
public record AdminQueueOverviewResponse(
    Long gameId,
    BookingStatus bookingStatus,
    long waitingCount,
    long usableAdmissionCount,
    long admittedToday,
    LocalDateTime collectedAt
) {
}
