package com.backtoback.reseat.domain.reservation.repository;

import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    List<ReservationSeat> findByReservation_Id(Long reservationId);
}
