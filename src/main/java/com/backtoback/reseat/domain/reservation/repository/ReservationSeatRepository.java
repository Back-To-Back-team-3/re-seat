package com.backtoback.reseat.domain.reservation.repository;

import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    List<ReservationSeat> findByReservation_Id(Long reservationId);

    /**
     * 특정 사용자가 특정 경기에서 유효하게 보유 중인 HOLDING 좌석 수를 센다.
     * <p>
     * CONFIRMED 예약은 티켓 기준(TicketCountPort)으로 집계되므로 제외한다(중복 계산 방지).
     * 만료 시각이 지난 예약은 스케줄러가 아직 회수하지 않았더라도 실질 보유가 아니므로 제외한다.
     */
    @Query("""
        SELECT COUNT(rs)
        FROM ReservationSeat rs
        JOIN rs.reservation r
        WHERE r.user.id = :userId
          AND r.game.id = :gameId
          AND r.status = com.backtoback.reseat.domain.reservation.entity.ReservationStatus.HOLDING
          AND r.holdExpiresAt > :now
        """)
    int countActiveHoldingSeats(
        @Param("userId") Long userId,
        @Param("gameId") Long gameId,
        @Param("now") LocalDateTime now
    );
}
