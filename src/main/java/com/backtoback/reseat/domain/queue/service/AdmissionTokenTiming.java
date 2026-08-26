package com.backtoback.reseat.domain.queue.service;

import java.time.LocalDateTime;

/**
 * 재선점 상한 검사 등 시간 기반 정책 판단에 필요한 토큰 타이밍 정보.
 *
 * @param expiresAt Queue-Token 만료 시각
 * @param seatBrowsingCompletedAt 최초 좌석 탐색 완료 시각. 최초 선점 전이면 null
 */
public record AdmissionTokenTiming(LocalDateTime expiresAt, LocalDateTime seatBrowsingCompletedAt) {
}
