package com.backtoback.reseat.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 선점 상세 조회 (ReservationSeat + GameSeat 페치 조인).
     *
     * <p>해제 시 좌석 상태를 되돌리기 위해 자식 컬렉션을 함께 로딩한다.
     * 컬렉션 페치 조인은 reservationSeats 하나만 사용(MultipleBagFetchException 방지).
     */
    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.reservationSeats rs
        LEFT JOIN FETCH rs.gameSeat
        WHERE r.id = :id
        """)
    Optional<Reservation> findWithSeatsById(@Param("id")
    Long id);

    /**
     * HOLDING 상태이면서 선점 만료 시각이 지난 예약을 EXPIRED로 벌크 전이한다.
     * <p>
     * 주문 생성은 같은 예약 행을 비관적 락으로 잡고 hold_expires_at을 연장한다.
     * 스케줄러가 SELECT 후 애플리케이션에서 만료를 판정하면 연장이 반영되기 전 판정값으로
     * 정상 예약을 EXPIRED로 전이하는 lost update가 발생한다.
     * WHERE 절에 만료 조건을 직접 실어 DB 원자성으로 경합을 흡수한다. (관련 버그 B5)
     * <p>
     * 벌크 UPDATE는 JPA 1차 캐시를 우회하므로 clearAutomatically = true로
     * 같은 트랜잭션 내 후속 조회의 stale 상태를 방지한다.
     * <p>
     * 인덱스: idx_reservations_status_expires(status, hold_expires_at)
     *
     * @param now 만료 판정 기준 시각 (HoldExpiryService가 주입)
     * @return EXPIRED로 전이된 예약 행 수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        update Reservation r
           set r.status = :expired
         where r.status = :holding
           and r.holdExpiresAt < :now
        """)
    int expireHoldingReservations(
        @Param("now")
        LocalDateTime now,
        @Param("holding")
        ReservationStatus holding,
        @Param("expired")
        ReservationStatus expired);

    // C-4: findByReservationNo, findByUserIdAndStatus...
}
