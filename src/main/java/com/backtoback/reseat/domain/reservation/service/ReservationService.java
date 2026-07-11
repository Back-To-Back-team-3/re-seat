package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.dto.HoldTimeResponse;
import com.backtoback.reseat.domain.reservation.dto.ReservationCancelResponse;
import com.backtoback.reseat.domain.reservation.dto.ReservationResponse;
import com.backtoback.reseat.domain.reservation.dto.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약(선점) 도메인 서비스.
 *
 * NOTE: 이 서비스는 의도적으로 락(Lock) 미적용 상태입니다.
 * 동시 요청 시 over-booking이 발생할 수 있습니다.
 * → C-4에서 over-booking 재현 → Redisson 분산락 도입 → 방어 성공 서사로 연결하려고 계획중입니다.
 *
 * <p>이 사이클의 범위:
 *   - Happy path 기본 흐름만 구현. @Transactional 적용, SELECT FOR UPDATE 없음.
 *   - 상태 전이 검증 없음 → C-3
 *   - TTL 만료 스케줄러 없음 → C-3 holdExpiresAt 세팅만.
 *   - 본인 검증 강화 없음 → C-3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final ReservationRepository reservationRepository;
    private final GameSeatRepository gameSeatRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ReservationNumberGenerator reservationNumberGenerator;

    /**
     * 좌석을 선점합니다 (HOLD).
     *
     * <p> **락 미적용: 4번 단계에서 AVAILABLE 확인 후 HELD 전환 사이에
     * 다른 트랜잭션이 끼어들면 동일 좌석이 중복 선점됩니다. → C-4 서사 준비.
     *
     * @param userId  인증 사용자 ID
     * @param request 선점 요청 DTO
     * @return 선점 결과 응답 DTO
     */
    @Transactional
    public ReservationResponse holdSeats(Long userId, SeatHoldRequest request) {
        // 1. User 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Game 조회
        Game game = gameRepository.findById(request.gameId())
            .orElseThrow(() -> new GameNotFoundException(request.gameId()));

        // 3. GameSeat 조회 (요청 개수와 실제 조회 개수가 다르면 존재하지 않는 좌석 포함)
        List<GameSeat> gameSeats = gameSeatRepository.findAllById(request.gameSeatIds());
        if (gameSeats.size() != request.gameSeatIds().size()) {
            throw new BusinessException(ErrorCode.GAME_SEAT_NOT_FOUND);
        }

        // 4. 경기 소속 + AVAILABLE 확인
        //    ** 아직 락 없음: 이 체크와 상태 변경 사이에 over-booking 발생 가능 → C-4
        validateSeatsForGame(gameSeats, game);

        // 5. 만료 시각은 한 번만 계산해 Reservation·GameSeat이 동일 값이 되도록 보장
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(HOLD_TTL);

        Reservation reservation = Reservation.builder()
            .user(user)
            .game(game)
            .reservationNo(reservationNumberGenerator.generate())
            .status(ReservationStatus.HOLDING)
            .holdExpiresAt(expiresAt)
            .build();

        // 6. ReservationSeat 생성 (price 스냅샷) + 연관관계 편의 메서드로 연결
        for (GameSeat gs : gameSeats) {
            ReservationSeat rs = ReservationSeat.builder()
                .gameSeat(gs)
                .price(gs.getPrice())
                .build();
            reservation.addReservationSeat(rs);   // 양방향 정합성 + cascade 저장
        }

        // 7. GameSeat 상태 AVAILABLE → HELD
        gameSeats.forEach(gs -> {
            gs.updateStatus(GameSeatStatus.HELD);
            gs.updateHoldExpiresAt(expiresAt);
        });

        // 8. save (cascade = ALL 이므로 ReservationSeat 함께 영속화)
        reservationRepository.save(reservation);

        log.info("[ReservationService] 좌석 선점 완료. reservationId={}, userId={}, seats={}",
            reservation.getId(), userId, request.gameSeatIds());

        return ReservationResponse.from(reservation);
    }

    /**
     * 선점 남은 시간을 조회한다.
     */
    @Transactional(readOnly = true)
    public HoldTimeResponse getHoldTime(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 410 Gone (만료 예약) 처리는 C-3에서 추가
        return HoldTimeResponse.from(reservation);
    }

    /**
     * 선점을 해제한다.
     *
     * <p>소유자 검증 강화(403 세부 처리)·상태 전이 검증은 C-3에서 처리.
     *
     * @param reservationId 예약 ID
     * @param userId        인증 사용자 ID
     * @return 해제 결과 응답 DTO
     */
    @Transactional
    public ReservationCancelResponse releaseHold(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findWithSeatsById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 기본 소유자 확인 — 상세 예외 처리는 C-3
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // GameSeat 상태 HELD → AVAILABLE 복귀 (즉시 재선점 가능)
        reservation.getReservationSeats().forEach(rs -> {
            rs.getGameSeat().updateStatus(GameSeatStatus.AVAILABLE);
            rs.getGameSeat().updateHoldExpiresAt(null);
        });

        // Reservation 상태 HOLDING → CANCELED
        // 상태 전이 검증(HOLDING이 아닌데 CANCELED 시도 등)은 C-3에서 추가
        reservation.updateStatus(ReservationStatus.CANCELED);

        log.info("[ReservationService] 선점 해제 완료. reservationId={}, userId={}",
            reservationId, userId);

        return ReservationCancelResponse.from(reservation);
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
