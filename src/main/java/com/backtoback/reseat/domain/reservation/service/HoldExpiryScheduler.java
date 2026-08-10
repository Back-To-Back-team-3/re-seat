package com.backtoback.reseat.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HOLD TTL 만료 스케줄러.
 * <p>
 * fixedDelay 방식으로 이전 실행이 끝난 뒤 delay만큼 대기 후 재실행하므로 실행 중첩이 없다.
 * 초 단위 정밀도가 불필요한 데모 규모에서 cron보다 단순하다.
 * <p>
 * 스케줄러는 now를 주입해 HoldExpiryService를 호출하고 결과를 로깅하는 역할만 담당한다.
 * 만료 판정·상태 전이·트랜잭션 경계는 모두 서비스 계층에 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final HoldExpiryService holdExpiryService;

    /**
     * 만료된 선점을 주기적으로 회수한다.
     *
     * <p>fixedDelay = 10초 (데모 기준. 운영 환경에서는 application.yml로 외부화 권장).
     * initialDelay = 30초로 두어 앱 기동 직후 다른 초기화 작업과의 경합을 회피한다.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 30_000)
    public void sweepExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        HoldExpiryService.HoldExpiryResult result = holdExpiryService.releaseExpired(now);

        // 회수 건수가 있을 때만 INFO — 0건 반복은 DEBUG로 억제 (Git 매뉴얼 로깅 정책)
        if (result.total() > 0) {
            log.info("[HoldExpiry] 만료 선점 회수 완료 — 예약 {}건 EXPIRED, 좌석 {}건 AVAILABLE",
                result.expiredReservations(), result.releasedSeats());
        } else {
            log.debug("[HoldExpiry] 만료 선점 없음 (기준 시각: {})", now);
        }
    }
}
