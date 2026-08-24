package com.backtoback.reseat.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;
import com.backtoback.reseat.domain.reservation.service.port.TicketCountPort;
import com.backtoback.reseat.domain.reservation.service.port.UserVerificationPort;
import com.backtoback.reseat.domain.user.exception.UserNotVerifiedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선점 흐름을 조율하는 Facade.
 * <p>트랜잭션 경계 바깥에서 락을 소유하고,
 * 내부에서 ReservationService holdSeats의 트랜잭션이 커밋된 뒤 락을 해제한다.</p>
 * <p>@Transactional 금지 — Facade에 트랜잭션을 걸면 커밋 전에 락이 해제돼 over-booking이 재발한다.</p>
 * <p>흐름:</p>
 *
 * <pre>
 * 1. isVerified — 본인 인증 완료 여부 검증, USER_NOT_VERIFIED(403)
 * 2. validateToken — 불필요한 락 획득 방지 (락 전 사전 검증)
 * 3. 수량 검증 — 누적 보유 좌석 수 기준, MAX_SEAT_COUNT_EXCEEDED(400)
 * 4. executeWithLocks → holdSeats (트랜잭션·커밋)
 * 5. completeSeatBrowsing — 최초 좌석 탐색 완료 시각 기록 (실패해도 선점은 유지)
 * </pre>
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

    /**
     * 좌석 선점 요청을 처리한다.
     * 본인 인증 검증 → Queue-Token 검증 → 수량 검증 → 분산 락 획득 → 좌석 선점 트랜잭션 → 좌석 탐색 완료 기록 순으로 처리한다.
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

        // 2단계: 락 획득 전 토큰 사전 검증 — 유효하지 않은 요청이 락까지 진입하는 것을 차단
        admissionTokenService.validateToken(userId, request.gameId(), token);

        // 3단계: 예매 수량 — 누적 보유 좌석 수 기준 검증
        // 집계 ① HOLDING 예약(만료 제외) + 집계 ② 유효 티켓(stub, 0 반환 — T3-06에서 실제 구현으로 교체)
        int heldSeatCount
            = reservationSeatRepository.countActiveHoldingSeats(userId, request.gameId(), LocalDateTime.now())
                + ticketCountPort.countActiveTickets(userId, request.gameId());

        SeatCountPolicy.validateSeatCount(heldSeatCount, request.gameSeatIds().size());

        // 4단계: 좌석 단위 분산 락 획득 → 선점 트랜잭션 실행(커밋) → 락 해제
        ReservationResponse response
            = seatLockStrategy
                .executeWithLocks(request.gameSeatIds(), () -> reservationService.holdSeats(userId, request));

        // 5단계: 최초 좌석 탐색 완료 시각 기록 — expireBrowsing이 이 기록을 전제로 동작한다.
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
