package com.backtoback.reseat.domain.reservation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

	List<ReservationSeat> findByReservation_Id(Long reservationId);
}
