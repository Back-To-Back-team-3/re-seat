package com.backtoback.reseat.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenTiming;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.UserGameLockStrategy;
import com.backtoback.reseat.domain.reservation.service.port.TicketCountPort;
import com.backtoback.reseat.domain.reservation.service.port.UserVerificationPort;
import com.backtoback.reseat.domain.user.exception.UserNotVerifiedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선점 흐름을 조율하는 Facade.
 * <p>@Transactional 금지 — 커밋 전 락 해제로 인한 over-booking 재발 방지.</p>
 * <p>흐름:</p>
 *
 * <pre>
 * 1. isVerified — 본인 인증 완료 여부 검증, USER_NOT_VERIFIED(403)
 * 2. validateToken — Queue-Token 검증(소비하지 않음)
 *    2-1. validateExtensionLimit — 재선점 HOLD 상한 보정, HOLD_EXTENSION_LIMIT_EXCEEDED(409)
 * 3. userGameLock 진입 — 아래 3-1, 3-2를 원자적 구간으로 묶는다.
 *    3-1. 수량 검증 — 누적 보유 좌석 수 기준, MAX_SEAT_COUNT_EXCEEDED(400)
 *    3-2. executeWithLocks → holdSeats (좌석 락 + 트랜잭션·커밋)
 * 4. completeSeatBrowsing — 최초 좌석 탐색 완료 시각 기록 (락 밖, 실패해도 선점 유지)
 * </pre>
 *
 * <p>락 획득 순서: userGameLock(바깥) → 좌석 락(안쪽). 이 순서를 뒤집지 않는다(데드락 방지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldFacade {

    private final ReservationService reservationService;
    private final SeatLockStrategy seatLockStrategy;
    private final AdmissionTokenService admissionTokenService;
    private final ReservationSeatRepository reservationSeatRepository;
    private final TicketCountPort ticketCountPort;
    private final UserVerificationPort userVerificationPort;
    private final UserGameLockStrategy userGameLockStrategy;

    /**
     * 좌석 선점 요청을 처리한다.
     * 본인 인증 검증 → Queue-Token 검증 → 재선점 HOLD 상한 보정 → 수량 검증 → 분산 락 획득 → 좌석 선점 트랜잭션 → 좌석 탐색 완료 기록 순으로 처리한다.
     *
     * @param userId 인증 사용자 ID
     * @param token Queue-Token 헤더 값
     * @param request 선점 요청 DTO
     * @return 선점 결과 응답 DTO
     */
    public ReservationResponse holdSeats(Long userId, String token, SeatHoldRequest request) {
        // 1단계: 예매 자격 — 본인 인증 완료자만 선점 가능
        if (!userVerificationPort.isVerified(userId)) {
            throw new UserNotVerifiedException();
        }

        // 2단계: 락 획득 전 Queue-Token 사전 검증 — 유효하지 않은 요청이 락까지 진입하는 것을 차단
        admissionTokenService.validateToken(userId, request.gameId(), token);

        // 2-1단계: 재선점 HOLD 상한 보정
        AdmissionTokenTiming timing = admissionTokenService.getTokenTiming(userId, request.gameId(), token);
        HoldExtensionPolicy.validateExtensionLimit(timing, LocalDateTime.now());

        // 3단계: 사용자·경기 단위 락으로 "수량 검증 → 좌석 락·선점"을 원자적으로 묶는다.
        ReservationResponse response = userGameLockStrategy.executeWithLock(userId, request.gameId(), () -> {
            // 3-1. 예매 수량 — 누적 보유 좌석 수 기준 검증
            int heldSeatCount
                = reservationSeatRepository.countActiveHoldingSeats(userId, request.gameId(), LocalDateTime.now())
                    + ticketCountPort.countActiveTickets(userId, request.gameId());

            SeatCountPolicy.validateSeatCount(heldSeatCount, request.gameSeatIds().size());

            // 3-2. 좌석 단위 분산 락 획득 → 선점 트랜잭션 실행(커밋) → 락 해제
            return seatLockStrategy
                .executeWithLocks(request.gameSeatIds(), () -> reservationService.holdSeats(userId, request));
        });

        // 4단계: 최초 좌석 탐색 완료 시각 기록 — expireBrowsing이 이 기록을 전제로 동작한다.
        recordSeatBrowsingCompleted(userId, request.gameId(), token);

        log
            .info(
                "[SeatHoldFacade] 좌석 선점 완료. userId={}, gameId={}, seats={}",
                userId,
                request.gameId(),
                request.gameSeatIds()
            );

        return response;
    }

    /**
     * 최초 좌석 탐색 완료 시각을 기록한다.
     * <p>실패해도 좌석 선점 자체는 성공으로 유지한다.
     * 기록 실패는 정합성 위반이 아니므로 좌석 점유 상한은 Queue-Token TTL(21분)이 백스톱한다.
     */
    private void recordSeatBrowsingCompleted(Long userId, Long gameId, String token) {
        try {
            admissionTokenService.completeSeatBrowsing(userId, gameId, token);
        } catch (Exception e) {
            log.warn("좌석 탐색 완료 기록 실패 - userId={}, gameId={}", userId, gameId, e);
        }
    }
}
