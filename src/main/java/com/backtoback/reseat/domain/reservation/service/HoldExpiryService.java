package com.backtoback.reseat.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 만료된 좌석 선점 회수 서비스.
 * <p>
 * HOLDING 상태이면서 hold_expires_at이 지난 예약을 EXPIRED로, 묶인 HELD 좌석을 AVAILABLE로 벌크 전이한다.
 * <p>
 * 트랜잭션 경계를 이 서비스에 두어 두 UPDATE가 원자적으로 처리되도록 한다.
 * 스케줄러(@Scheduled)에 @Transactional을 붙이면 프록시 self-invocation 이슈가 생기므로,
 * 스케줄러는 이 메서드를 호출하는 역할만 담당한다.
 * <p>
 * UPDATE 순서: reservations → game_seats.
 * 주문 생성이 예약 행을 먼저 비관적 락으로 잡은 뒤 좌석을 갱신하므로,
 * 스케줄러도 같은 순서로 행을 갱신해 데드락 가능성을 줄인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldExpiryService {

    private final ReservationRepository reservationRepository;
    private final GameSeatRepository gameSeatRepository;

    /**
     * 만료된 선점을 회수한다.
     * <p>
     * 두 UPDATE가 같은 트랜잭션에 묶여 있어,
     * 예약만 EXPIRED로 바뀌고 좌석은 HELD로 남는 부분 회수가 발생하지 않는다.
     *
     * @param now 만료 판정 기준 시각 (HoldExpiryScheduler가 주입)
     * @return 회수된 예약/좌석 건수
     */
    @Transactional
    public HoldExpiryResult releaseExpired(LocalDateTime now) {
        int expiredReservations = reservationRepository.expireHoldingReservations(
            now,
            ReservationStatus.HOLDING,
            ReservationStatus.EXPIRED);

        int releasedSeats = gameSeatRepository.releaseExpiredSeats(
            now,
            GameSeatStatus.HELD,
            GameSeatStatus.AVAILABLE);

        return new HoldExpiryResult(expiredReservations, releasedSeats);
    }

    /**
     * 만료 회수 결과 반환값.
     *
     * @param expiredReservations EXPIRED로 전이된 예약 행 수
     * @param releasedSeats       AVAILABLE로 회수된 좌석 행 수
     */
    public record HoldExpiryResult(int expiredReservations, int releasedSeats) {

        public int total() {
            return expiredReservations + releasedSeats;
        }
    }
}
