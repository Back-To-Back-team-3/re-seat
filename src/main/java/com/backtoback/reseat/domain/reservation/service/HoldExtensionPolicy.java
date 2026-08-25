package com.backtoback.reseat.domain.reservation.service;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenTiming;
import com.backtoback.reseat.domain.reservation.exception.HoldExtensionLimitExceededException;

/**
 * 재선점 시 HOLD 상한 보정 정책.
 * <p>조건 1(대기열 잔여 TTL)과 조건 2(상한 보정)를 함께 검증한다.
 * seatBrowsingCompletedAt이 null이면(최초 선점) 조건 2 계산을 생략한다.
 */
public final class HoldExtensionPolicy {

    private HoldExtensionPolicy() {}

    /**
     * 재선점 시 Queue-Token 잔여 TTL과 HOLD 상한을 검증한다.
     *
     * @param timing 조회한 Queue-Token 타이밍 정보
     * @param attemptedAt 이번 선점 시도 시각
     */
    public static void validateExtensionLimit(AdmissionTokenTiming timing, LocalDateTime attemptedAt) {

        // 조건 1: Queue-Token 잔여 TTL > 0 (validateToken의 410과 별개의 방어선)
        if (!timing.expiresAt().isAfter(attemptedAt)) {
            throw new QueueTokenExpiredException();
        }

        // 조건 2: 최초 선점 전이면(=최초 선점 시도) 상한 검사 자체를 생략한다.
        LocalDateTime seatBrowsingCompletedAt = timing.seatBrowsingCompletedAt();
        if (seatBrowsingCompletedAt == null) {
            return;
        }

        LocalDateTime limit = seatBrowsingCompletedAt.plus(HoldPolicy.HOLD_EXTEND_CAP);
        LocalDateTime deadlineIfAccepted = attemptedAt.plus(HoldPolicy.PAYMENT_DEADLINE);

        if (deadlineIfAccepted.isAfter(limit)) {
            throw new HoldExtensionLimitExceededException();
        }
    }
}
