package com.backtoback.reseat.domain.reservation.repository;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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
    Optional<Reservation> findWithSeatsById(@Param("id") Long id);

    // C-4: findByReservationNo, findByUserIdAndStatus, findExpiredHolding ...
}
