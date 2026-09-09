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
import com.backtoback.reseat.domain.reservation.exception.PreReservationExpiredException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationNotFoundException;
import com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatStatusService;
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
 * holdSeats()는 락 획득 이후 실행되므로, 좌석 상태 재검증이 over-booking 방어의 최종 게이트 역할을 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final GameSeatRepository gameSeatRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ReservationNumberGenerator reservationNumberGenerator;
    private final GameSeatStatusService gameSeatStatusService;

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
     * <p>동시 취소·해제 요청이 좌석 반환을 중복 실행하지 않도록 예약 행을 비관적 락으로 잠근다.</p>
     *
     * @param reservationId 예약 ID
     * @param requesterId 인증 사용자 ID
     * @return 해제 결과 응답 DTO
     */
    @Transactional
    public ReservationCancelResponse releaseHold(Long reservationId, Long requesterId) {
        // 동시 취소·해제 요청이 좌석 반환을 중복 실행하지 않도록 예약 행을 비관적 락으로 잠근다.
        Reservation reservation
            = reservationRepository
                .findByIdWithPessimisticWriteLock(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 1. 소유권 가드 — 타인 예약의 존재 여부가 상태 코드로 노출되지 않도록 최우선 수행
        verifyOwner(reservation, requesterId);

        LocalDateTime now = LocalDateTime.now();

        // 2. 만료 전용 가드 — 잘못된 상태 전이(409)와 만료(410)를 구분한다.
        if (reservation.isExpired(now)) {
            throw new PreReservationExpiredException();
        }

        // 3. 재취소 멱등 처리 — 이미 취소된 예약의 재취소는 새로운 실패가 아닌 현재 상태와 200으로 반환한다.
        // 좌석은 최초 취소 시점에 이미 반환됐으므로 release()를 다시 호출하지 않는다.
        if (reservation.isCanceled()) {
            return ReservationCancelResponse.from(reservation);
        }

        // 4. 정상 취소 전이 — 좌석 목록은 락 범위 밖에서 별도 조회한다.
        // 컬렉션 fetch join + 비관적 락 동시 사용 회피한다.
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservation_Id(reservationId);
        reservationSeats.forEach(rs -> gameSeatStatusService.releaseSeat(rs.getGameSeat().getId()));

        // Reservation 상태 HOLDING → CANCELED
        reservation.cancel();

        log.info("[ReservationService] 선점 해제 완료. reservationId={}, userId={}", reservationId, requesterId);

        return ReservationCancelResponse.from(reservation);
    }

    /**
     * 결제 전 예약을 취소 상태로 변경한다.
     * <p>HOLDING 상태의 예약을 취소하고, 묶인 좌석을 모두 반환한다.
     * 이미 취소된 예약은 상태를 다시 변경하지 않고 좌석 반환도 다시 시도하지 않는다.</p>
     * <p>동시 취소 요청 중 하나만 좌석 반환을 실행하도록 예약 행을 비관적 락으로 잠근다.</p>
     *
     * @param reservationId 취소 처리할 예약 ID
     */
    @Transactional
    public void cancel(Long reservationId) {

        Reservation reservation
            = reservationRepository
                .findByIdWithPessimisticWriteLock(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.isCanceled()) {
            return;
        }

        reservation.cancel();

        // 결제 전 취소는 예약 전체가 대상이며 부분 취소 개념이 없으므로, 예약에 묶인 좌석을 모두 반환해도 안전하다.
        // 주의: 좌석은 취소 시점에 HELD 상태이므로 refundSeat()가 아닌 releaseSeat()를 호출한다.
        // 좌석 목록은 락 범위 밖에서 별도 조회한다(컬렉션 fetch join + 비관적 락 동시 사용 회피).
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservation_Id(reservationId);
        reservationSeats.forEach(rs -> gameSeatStatusService.releaseSeat(rs.getGameSeat().getId()));
    }

    /**
     * 결재 완료 후 환불이 확정된 예약을 취소 상태로 변경한다.
     * <p>CONFIRMED 상태의 예약을 취소하고,
     * 이미 취소된 예약은 상태를 다시 변경하지 않는다.</p>
     *
     * @param reservationId 취소 처리할 예약 ID
     */
    @Transactional
    public void cancelConfirmed(Long reservationId) {

        Reservation reservation
            = reservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.isCanceled()) {
            return;
        }

        reservation.cancelConfirmed();
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
