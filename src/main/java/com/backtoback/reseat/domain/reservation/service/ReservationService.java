package com.backtoback.reseat.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.HoldTimeResponse;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationCancelResponse;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationNotFoundException;
import com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약(선점) 도메인 서비스.
 * <p>
 * NOTE: 이 서비스는 SeatHoldFacade의 분산 락 안에서 호출된다.
 * holdSeats()}는 락 획득 이후 실행되므로, 좌석 상태 재검증이 over-booking 방어의 최종 게이트 역할을 한다.
 * <p>
 * C-4-1 변경 사항:
 * - HOLD_TTL 5분 → HoldPolicy.HOLD_TTL(10분) 정합.
 * - holdSeats(): gs.updateStatus/updateHoldExpiresAt → gs.hold(expiresAt) 도메인 메서드로 교체.
 * - releaseHold(): updateStatus/updateHoldExpiresAt → rs.getGameSeat().release() 로 교체.
 * - releaseHold(): reservation.updateStatus(CANCELED) → reservation.cancel() 로 교체.
 * C-5-2 변경 사항:
 * - holdSeats() 락 획득 후 재검증 주석 명확화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final GameSeatRepository gameSeatRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ReservationNumberGenerator reservationNumberGenerator;

    /**
     * 좌석을 선점합니다 (HOLD).
     * <p>
     * 락 미적용: 4번 단계에서 AVAILABLE 확인 후 HELD 전환 사이에
     * 다른 트랜잭션이 끼어들면 동일 좌석이 중복 선점됩니다. → C-5 서사 준비.
     *
     * @param userId 인증 사용자 ID
     * @param request 선점 요청 DTO
     * @return 선점 결과 응답 DTO
     */
    @Transactional
    public ReservationResponse holdSeats(Long userId, SeatHoldRequest request) {
        // 1. User 조회
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Game 조회
        Game game
            = gameRepository.findById(request.gameId()).orElseThrow(() -> new GameNotFoundException(request.gameId()));

        // 3. GameSeat 조회 (요청 개수와 실제 조회 개수가 다르면 존재하지 않는 좌석 포함)
        List<GameSeat> gameSeats = gameSeatRepository.findAllById(request.gameSeatIds());
        if (gameSeats.size() != request.gameSeatIds().size()) {
            throw new BusinessException(ErrorCode.GAME_SEAT_NOT_FOUND);
        }

        // 4. 경기 소속 + AVAILABLE 확인
        // 락 획득 후 재조회·재검증 — Facade의 분산 락 안에서 실행되므로 이 시점의 상태가 실제 최신 상태다.
        // over-booking 방어의 핵심 지점.
        validateSeatsForGame(gameSeats, game);

        // 5. 만료 시각은 한 번만 계산해 Reservation·GameSeat이 동일 값이 되도록 보장
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = HoldPolicy.holdExpiresAt(now);

        // 6. Reservation 생성
        Reservation reservation
            = Reservation
                .builder()
                .user(user)
                .game(game)
                .reservationNo(reservationNumberGenerator.generate())
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(expiresAt)
                .build();

        // 7. ReservationSeat 생성 (price 스냅샷) + 연관관계 편의 메서드로 연결
        for (GameSeat gs : gameSeats) {
            ReservationSeat rs = ReservationSeat.builder().gameSeat(gs).price(gs.getPrice()).build();
            reservation.addReservationSeat(rs); // 양방향 정합성 + cascade 저장
        }

        // 8. GameSeat 상태 AVAILABLE → HELD (도메인 메서드: 전이 가드 + holdExpiresAt 원자 세팅)
        gameSeats.forEach(gs -> gs.hold(expiresAt));

        // 9. save (cascade = ALL 이므로 ReservationSeat 함께 영속화)
        reservationRepository.save(reservation);

        log
            .info(
                "[ReservationService] 좌석 선점 완료. reservationId={}, userId={}, seats={}",
                reservation.getId(),
                userId,
                request.gameSeatIds()
            );

        return ReservationResponse.from(reservation);
    }

    /**
     * 선점 남은 시간을 조회한다.
     *
     * @param reservationId 예약 ID
     * @param requesterId 인증 사용자 ID
     * @return 남은 시간 응답 DTO
     */
    @Transactional(readOnly = true)
    public HoldTimeResponse getHoldTime(Long reservationId, Long requesterId) {
        Reservation reservation
            = reservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 소유권 가드: 조회 직후·반환 전 위치 — 권한 없는 요청자에게 상태 정보를 흘리지 않음
        verifyOwner(reservation, requesterId);

        return HoldTimeResponse.from(reservation);
    }

    /**
     * 선점을 해제한다.
     *
     * @param reservationId 예약 ID
     * @param requesterId 인증 사용자 ID
     * @return 해제 결과 응답 DTO
     */
    @Transactional
    public ReservationCancelResponse releaseHold(Long reservationId, Long requesterId) {
        Reservation reservation
            = reservationRepository
                .findWithSeatsById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 소유권 가드: 조회 직후·상태 전이 전 위치 — 권한 없는 요청자에게 상태 정보를 흘리지 않음
        verifyOwner(reservation, requesterId);

        // GameSeat 상태 HELD → AVAILABLE 복귀 (도메인 메서드: 전이 가드 + holdExpiresAt null)
        reservation.getReservationSeats().forEach(rs -> rs.getGameSeat().release());

        // Reservation 상태 HOLDING → CANCELED (도메인 메서드: requireHolding 가드 포함)
        reservation.cancel();

        log.info("[ReservationService] 선점 해제 완료. reservationId={}, userId={}", reservationId, requesterId);

        return ReservationCancelResponse.from(reservation);
    }

    /**
     * 예약 소유자를 검증한다.
     * <p>
     * 요청자(requesterId)가 예약 소유자(reservation.user.id)와 다르면
     * {@link ReservationAccessDeniedException}을 던진다.
     * <p>
     * 호출 위치: findById 직후, 상태 전이 전.
     * 권한 없는 요청자에게 예약 상태 정보를 흘리지 않기 위해 이 순서를 유지한다.
     *
     * @param reservation 조회된 예약 엔티티
     * @param requesterId 인증 사용자 ID
     */
    private void verifyOwner(Reservation reservation, Long requesterId) {
        if (!reservation.getUser().getId().equals(requesterId)) {
            throw new ReservationAccessDeniedException();
        }
    }

    private void validateSeatsForGame(List<GameSeat> gameSeats, Game game) {
        for (GameSeat gs : gameSeats) {
            // 요청 좌석이 해당 경기에 속하는지 확인 (400 GAME_SEAT_NOT_IN_GAME)
            if (!gs.getGame().getId().equals(game.getId())) {
                throw new BusinessException(ErrorCode.GAME_SEAT_NOT_IN_GAME);
            }
            // AVAILABLE 확인 ← race condition 지점 (락 없음, 409 SEAT_ALREADY_HELD)
            if (gs.getStatus() != GameSeatStatus.AVAILABLE) {
                throw new SeatAlreadyHeldException(gs.getId());
            }
        }
    }
}
