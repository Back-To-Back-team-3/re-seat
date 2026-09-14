package com.backtoback.reseat.domain.reservation.service.policy;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenTiming;
import com.backtoback.reseat.domain.reservation.exception.HoldExtensionLimitExceededException;
import com.backtoback.reseat.domain.reservation.service.HoldExtensionPolicy;

/**
 * HoldExtensionPolicy 재선점 시 HOLD 상한 보정 단위 테스트.
 * <p>선점을 취소했다가 같은 Queue-Token으로 다시 선점할 때, 결제 기한이
 * "최초 선점 시각 + 18분" 상한을 넘지 않는지 확인한다. 상한을 넘으면
 * {@code HoldExtensionLimitExceededException}을 던져 재진입(토큰 재발급)을 유도한다.
 * <p>경계값(상한과 정확히 같은 경우는 통과, 1초라도 넘으면 차단)과,
 * 최초 선점(재선점이 아닌 경우)은 이 검사 자체를 건너뛴다는 예외 조건을 함께 고정한다.
 * <p>Queue-Token의 잔여 TTL이 이미 소진된 경우는 별도로 {@code QueueTokenExpiredException}을
 * 던지며, 이 판단이 HOLD 상한 검사보다 먼저 이뤄진다.
 */
@DisplayName("HoldExtensionPolicy 재선점 상한 보정")
class HoldExtensionPolicyTest {

    @Test
    @DisplayName("결제 마감 시각이 상한과 정확히 같으면 통과한다")
    void validateExtensionLimit_passes_whenDeadlineExactlyAtLimit() {
        AdmissionTokenTiming timing
            = new AdmissionTokenTiming(LocalDateTime.of(2026, 8, 20, 11, 0), LocalDateTime.of(2026, 8, 20, 10, 0));
        // seatBrowsingCompletedAt + 18분 = 10:18, attemptedAt + 8분 = 10:18 → 경계 정확히 일치
        LocalDateTime attemptedAt = LocalDateTime.of(2026, 8, 20, 10, 10);

        assertThatCode(() -> HoldExtensionPolicy.validateExtensionLimit(timing, attemptedAt))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("결제 마감 시각이 상한을 1초라도 넘으면 차단한다")
    void validateExtensionLimit_throws_whenDeadlineExceedsLimitByOneSecond() {
        AdmissionTokenTiming timing
            = new AdmissionTokenTiming(LocalDateTime.of(2026, 8, 20, 11, 0), LocalDateTime.of(2026, 8, 20, 10, 0));
        LocalDateTime attemptedAt = LocalDateTime.of(2026, 8, 20, 10, 10, 1);

        assertThatThrownBy(() -> HoldExtensionPolicy.validateExtensionLimit(timing, attemptedAt))
            .isInstanceOf(HoldExtensionLimitExceededException.class);
    }

    @Test
    @DisplayName("최초 선점(seatBrowsingCompletedAt = null)은 상한 검사를 생략하고 통과한다")
    void validateExtensionLimit_skips_whenSeatBrowsingCompletedAtIsNull() {
        AdmissionTokenTiming timing = new AdmissionTokenTiming(LocalDateTime.now().plusMinutes(20), null);

        assertThatCode(() -> HoldExtensionPolicy.validateExtensionLimit(timing, LocalDateTime.now()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Queue-Token 잔여 TTL이 0 이하면 조건 2와 무관하게 차단한다")
    void validateExtensionLimit_throwsQueueTokenExpired_whenTtlExhausted() {
        AdmissionTokenTiming timing
            = new AdmissionTokenTiming(LocalDateTime.now().minusSeconds(1), LocalDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> HoldExtensionPolicy.validateExtensionLimit(timing, LocalDateTime.now()))
            .isInstanceOf(QueueTokenExpiredException.class);
    }
}
